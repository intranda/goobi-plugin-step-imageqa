package de.intranda.goobi.plugins;

import java.awt.Dimension;
import java.awt.image.RenderedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibImageException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ImageManagerException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ImageManipulatorException;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageManager;
import de.unigoettingen.sub.commons.contentlib.imagelib.JpegInterpreter;
import de.unigoettingen.sub.commons.contentlib.servlet.controller.GetImageDimensionAction;
import lombok.Data;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@Data
@PluginImplementation
public class ImageQAPlugin implements IStepPlugin {

    private static final Logger logger = Logger.getLogger(ImageQAPlugin.class);
    private Step step;

    private static final String PLUGIN_NAME = "intranda_step_imageQA";

    private int NUMBER_OF_IMAGES_PER_PAGE = 10;
    private int THUMBNAIL_SIZE_IN_PIXEL = 175;
    private String THUMBNAIL_FORMAT = "png";
    private String MAINIMAGE_FORMAT = "jpg";
    private boolean allowDeletion = false;
    private boolean allowRotation = false;
    private boolean allowRenaming = false;
    private boolean allowSelection = false;
    private boolean allowDownload = false;

    private String rotationCommandLeft = "";
    private String rotationCommandRight = "";
    private String deletionCommand = "";
    boolean askForConfirmation = true;

    private int pageNo = 0;

    private int imageIndex = 0;

    private String imageFolderName = "";

    private List<Image> allImages = new ArrayList<Image>();

    private Image image = null;
    private List<String> imageSizes;

    private ExecutorService executor;

    private String returnPath;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        String projectName = step.getProzess().getProjekt().getTitel();
        HierarchicalConfiguration myconfig = null;

        // get the correct configuration for the right project
        List<HierarchicalConfiguration> configs = ConfigPlugins.getPluginConfig(this).configurationsAt("config");
        for (HierarchicalConfiguration hc : configs) {
            List<HierarchicalConfiguration> projects = hc.configurationsAt("project");
            for (HierarchicalConfiguration project : projects) {
                if (myconfig == null || project.getString("").equals("*") || project.getString("").equals(projectName)) {
                    myconfig = hc;
                }
            }
        }

        allowDeletion = myconfig.getBoolean("allowDeletion", false);
        allowRotation = myconfig.getBoolean("allowRotation", false);
        allowRenaming = myconfig.getBoolean("allowRenaming", false);
        allowSelection = myconfig.getBoolean("allowSelection", false);
        allowDownload = myconfig.getBoolean("allowDownload", false);

        deletionCommand = myconfig.getString("deletionCommand", "-");
        rotationCommandLeft = myconfig.getString("rotationCommands.left", "-");
        rotationCommandRight = myconfig.getString("rotationCommands.right", "-");

        NUMBER_OF_IMAGES_PER_PAGE = myconfig.getInt("numberOfImagesPerPage", 50);
        THUMBNAIL_SIZE_IN_PIXEL = myconfig.getInt("thumbnailsize", 200);
        THUMBNAIL_FORMAT = myconfig.getString("thumbnailFormat", "png");
        MAINIMAGE_FORMAT = myconfig.getString("mainImageFormat", "jpg");
        imageSizes = myconfig.getList("imagesize");
        if (imageSizes == null || imageSizes.isEmpty()) {
            imageSizes = new ArrayList<>();
            imageSizes.add("600");
        }
        executor = Executors.newFixedThreadPool(imageSizes.size());
        this.step = step;
        try {
            if (myconfig.getBoolean("useOrigFolder", false)) {
                imageFolderName = step.getProzess().getImagesOrigDirectory(false);
            } else {
                imageFolderName = step.getProzess().getImagesTifDirectory(false);
            }
            Path path = Paths.get(imageFolderName);
            if (Files.exists(path)) {
                List<String> imageNameList = NIOFileUtils.list(imageFolderName, NIOFileUtils.imageNameFilter);
                int order = 1;
                for (String imagename : imageNameList) {
                    Image currentImage = new Image(imagename, order++, "", imagename);
                    allImages.add(currentImage);
                }
                setImageIndex(0);
            }
        } catch (SwapException | DAOException | IOException | InterruptedException e) {
            logger.error(e);
        }
    }

    public List<Image> getPaginatorList() {
        List<Image> subList = new ArrayList<Image>();
        if (allImages.size() > (pageNo * NUMBER_OF_IMAGES_PER_PAGE) + NUMBER_OF_IMAGES_PER_PAGE) {
            subList = allImages.subList(pageNo * NUMBER_OF_IMAGES_PER_PAGE, (pageNo * NUMBER_OF_IMAGES_PER_PAGE) + NUMBER_OF_IMAGES_PER_PAGE);
        } else {
            subList = allImages.subList(pageNo * NUMBER_OF_IMAGES_PER_PAGE, allImages.size());
        }
        for (Image currentImage : subList) {
            if (StringUtils.isEmpty(currentImage.getThumbnailUrl())) {
                createImage(currentImage);
            }
        }
        return subList;
    }

    private void createImage(Image currentImage) {

        if (currentImage.getSize() == null) {
            currentImage.setSize(getActualImageSize(currentImage));
        }

        String thumbUrl = createImageUrl(currentImage, THUMBNAIL_SIZE_IN_PIXEL, THUMBNAIL_FORMAT, "");
        currentImage.setThumbnailUrl(thumbUrl);

        String largeThumbUrl = createImageUrl(currentImage, THUMBNAIL_SIZE_IN_PIXEL * 4, THUMBNAIL_FORMAT, "");
        currentImage.setLargeThumbnailUrl(largeThumbUrl);

        String contextPath = getContextPath();
        for (String sizeString : imageSizes) {
            try {
                int size = Integer.parseInt(sizeString);
                String imageUrl = createImageUrl(currentImage, size, MAINIMAGE_FORMAT, contextPath);
                currentImage.addImageLevel(imageUrl, size);
            } catch (NullPointerException | NumberFormatException e) {
                logger.error("Cannot build image with size " + sizeString);
            }
        }
        Collections.sort(currentImage.getImageLevels());
    }

    private String getContextPath() {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String baseUrl = session.getServletContext().getContextPath();
        return baseUrl;
    }

    private Dimension getActualImageSize(Image image) {
        Dimension dim;
        try {
            String imagePath = imageFolderName + image.getImageName();
            String dimString = new GetImageDimensionAction().getDimensions(imagePath);
            int width = Integer.parseInt(dimString.replaceAll("::.*", ""));
            int height = Integer.parseInt(dimString.replaceAll(".*::", ""));
            dim = new Dimension(width, height);
        } catch (NullPointerException | NumberFormatException | ContentLibImageException | URISyntaxException | IOException e) {
            logger.error("Could not retrieve actual image size", e);
            dim = new Dimension(0, 0);
        }
        return dim;
    }

    private String createImageUrl(Image currentImage, Integer size, String format, String baseUrl) {
        StringBuilder url = new StringBuilder(baseUrl);
        url.append("/cs").append("?action=").append("image").append("&format=").append(format).append("&sourcepath=").append("file://"
                + imageFolderName + currentImage.getImageName()).append("&width=").append(size).append("&height=").append(size);
        return url.toString();
    }

    @SuppressWarnings("unused")
    private Dimension scaleFile(String inFileName, String outFileName, List<String> sizes) throws IOException, ContentLibImageException {

        final ImageManager im = new ImageManager(new File(inFileName).toURI().toURL());
        Dimension originalImageSize = new Dimension(im.getMyInterpreter().getWidth(), im.getMyInterpreter().getHeight());
        String outputFilePath = FilenameUtils.getFullPath(outFileName);
        String outputFileBasename = FilenameUtils.getBaseName(outFileName);
        String outputFileSuffix = FilenameUtils.getExtension(outFileName);
        List<Future<File>> createdFiles = new ArrayList<>();
        for (String sizeString : sizes) {
            int size = Integer.parseInt(sizeString);
            final Dimension dim = new Dimension();
            dim.setSize(size, size);
            final String filename = outputFilePath + outputFileBasename + "_" + size + "." + outputFileSuffix;
            createdFiles.add(executor.submit(new Callable<File>() {

                @Override
                public File call() throws Exception {
                    return scaleToSize(im, dim, filename, false);
                }
            }));
        }
        while (!oneImageFinished(createdFiles)) {

        }
        logger.debug("First image finished generation");
        return originalImageSize;

    }

    @SuppressWarnings("unused")
    private boolean allImagesFinished(List<Future<File>> createdFiles) {
        for (Future<File> future : createdFiles) {
            try {
                if (!future.isDone() || future.get() == null) {
                    return false;
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }
        return true;
    }

    private boolean oneImageFinished(List<Future<File>> createdFiles) {
        for (Future<File> future : createdFiles) {
            try {
                if (future.isDone() && future.get() != null) {
                    return true;
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }
        return false;
    }

    private File scaleToSize(ImageManager im, Dimension dim, String filename, boolean overwrite) throws ImageManipulatorException,
            FileNotFoundException, ImageManagerException, IOException, ContentLibException {
        File outputFile = new File(filename);
        if (!overwrite && outputFile.isFile()) {
            return outputFile;
        }
        try (FileOutputStream outputFileStream = new FileOutputStream(outputFile)) {
            RenderedImage ri = im.scaleImageByPixel(dim, ImageManager.SCALE_TO_BOX, 0);
            try (JpegInterpreter pi = new JpegInterpreter(ri)) {
                pi.writeToStream(null, outputFileStream);
                outputFileStream.close();
                logger.debug("Written file " + outputFile);
                return outputFile;
            }
        }
    }

    @Override
    public boolean execute() {
        return false;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.FULL;
    }

    @Override
    public String getPagePath() {
        return "/" + getTheme() + "/ImageQAPlugin.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    public String getDescription() {
        return PLUGIN_NAME;
    }

    @Override
    public String cancel() {
        return "/" + getTheme() + returnPath;
    }

    @Override
    public String finish() {
        return "/" + getTheme() + returnPath;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    public void setImageIndex(int imageIndex) {
        this.imageIndex = imageIndex;
        if (this.imageIndex < 0) {
            this.imageIndex = 0;
        }
        if (this.imageIndex >= getSizeOfImageList()) {
            this.imageIndex = getSizeOfImageList() - 1;
        }
        if (this.imageIndex >= 0) {
            setImage(allImages.get(this.imageIndex));
        }
    }

    public String getBild() {
        if (image == null) {
            return null;
        } else {
            FacesContext context = FacesContextHelper.getCurrentFacesContext();
            HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
            String currentImageURL = session.getServletContext().getContextPath() + ConfigurationHelper.getTempImagesPath() + session.getId() + "_"
                    + image.getImageName() + "_large_" + ".jpg";
            return currentImageURL;
        }
    }

    public int getImageWidth() {
        if (image == null) {
            logger.error("Must set image before querying image size");
            return 0;
        } else if (image.getSize() == null) {
            createImage(image);
        }
        return image.getSize().width;
    }

    public int getImageHeight() {
        if (image == null) {
            logger.error("Must set image before querying image size");
            return 0;
        } else if (image.getSize() == null) {
            createImage(image);
        }
        return image.getSize().height;
    }

    @SuppressWarnings("unused")
    private String getImageUrl(Image image, String size) {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String currentImageURL = session.getServletContext().getContextPath() + ConfigurationHelper.getTempImagesPath() + session.getId() + "_"
                + image.getImageName() + "_large_" + size + ".jpg";
        return currentImageURL;
    }

    @SuppressWarnings("unused")
    private String getImagePath(Image image) {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String path = ConfigurationHelper.getTempImagesPathAsCompleteDirectory() + session.getId() + "_" + image.getImageName() + "_large" + ".jpg";
        return path;
    }

    public String cmdMoveFirst() {
        if (this.pageNo != 0) {
            this.pageNo = 0;
            getPaginatorList();
        }
        return "";
    }

    public String cmdMovePrevious() {
        if (!isFirstPage()) {
            this.pageNo--;
            getPaginatorList();
        }
        return "";
    }

    public String cmdMoveNext() {
        if (!isLastPage()) {
            this.pageNo++;
            getPaginatorList();
        }
        return "";
    }

    public String cmdMoveLast() {
        if (this.pageNo != getLastPageNumber()) {
            this.pageNo = getLastPageNumber();
            getPaginatorList();
        }
        return "";
    }

    public void setTxtMoveTo(String neueSeite) {
        try {
            int pageNumber = Integer.parseInt(neueSeite);
            if ((this.pageNo != pageNumber - 1) && pageNumber > 0 && pageNumber <= getLastPageNumber() + 1) {
                this.pageNo = pageNumber - 1;
                getPaginatorList();
            }
        } catch (NumberFormatException e) {
        }
    }

    public String getTxtMoveTo() {
        return this.pageNo + 1 + "";
    }

    public void setImageMoveTo(String page) {
        try {
            int pageNumber = Integer.parseInt(page);
            if ((this.imageIndex != pageNumber - 1) && pageNumber > 0 && pageNumber <= getSizeOfImageList() + 1) {
                setImageIndex(pageNumber - 1);
            }
        } catch (NumberFormatException e) {
        }
    }

    public String getImageMoveTo() {
        return this.imageIndex + 1 + "";
    }

    public int getLastPageNumber() {
        int ret = new Double(Math.floor(this.allImages.size() / NUMBER_OF_IMAGES_PER_PAGE)).intValue();
        if (this.allImages.size() % NUMBER_OF_IMAGES_PER_PAGE == 0) {
            ret--;
        }
        return ret;
    }

    public boolean isFirstPage() {
        return this.pageNo == 0;
    }

    public boolean isLastPage() {
        return this.pageNo >= getLastPageNumber();
    }

    public boolean hasNextPage() {
        return this.allImages.size() > NUMBER_OF_IMAGES_PER_PAGE;
    }

    public boolean hasPreviousPage() {
        return this.pageNo > 0;
    }

    public Long getPageNumberCurrent() {
        return Long.valueOf(this.pageNo + 1);
    }

    public Long getPageNumberLast() {
        return Long.valueOf(getLastPageNumber() + 1);
    }

    public int getSizeOfImageList() {
        return allImages.size();
    }

    public int getThumbnailSize() {
        return THUMBNAIL_SIZE_IN_PIXEL;
    }

    public void setThumbnailSize(int value) {

    }

    private String getTheme() {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        String completePath = context.getExternalContext().getRequestServletPath();
        if (StringUtils.isNotBlank(completePath)) {
            String[] parts = completePath.split("/");
            return parts[1];
        }
        return "";
    }

    public String renameImages(Image myimage) {
        DecimalFormat myFormatter = new DecimalFormat("0000");

        int myindex = getImageIndex();
        int generalCounter = 1;
        int fileCounter = 1;
        boolean imageFound = false;

        System.out.println("Dateien werden jetzt umbenannt auf der Basis von: " + myimage.getTempName());

        //Path path = Paths.get(imageFolderName + myimage.getImageName());
        for (Path f : NIOFileUtils.listFiles(imageFolderName)) {
            String filenameold = f.getFileName().toString();
            String prefix = myFormatter.format(generalCounter) + "_";
            String suffix = filenameold.substring(filenameold.lastIndexOf("."), filenameold.length());

            if (!imageFound && myimage.getImageName().equals(filenameold)) {
                imageFound = true;
                System.out.println("Bild gefunden: " + filenameold);
            }

            if (imageFound) {
                String filenamenew = prefix + myimage.getTempName() + "_" + myFormatter.format(fileCounter) + suffix;
                System.out.println(filenameold + " will be renamed to " + filenamenew);
                try {
                    NIOFileUtils.renameTo(f, filenamenew);
                } catch (IOException e) {
                    logger.error(e);
                }
                fileCounter++;
            } else {
                System.out.println(filenameold + " will not be renamed");
            }

            generalCounter++;

        }
        //        if (Files.exists(path)) {
        //            NIOFileUtils.deleteDir(path);
        //        }
        allImages = new ArrayList<Image>();
        initialize(this.step, "");

        for (Image image : allImages) {
            image.setTempName(myimage.getTempName());
        }

        setImageIndex(myindex);
        return "";
    }

    public void deleteImage(Image myimage) {
        callScript(myimage, deletionCommand, true);
    }

    public void rotateRight(Image myimage) {
        callScript(myimage, rotationCommandRight, false);
    }

    public void rotateLeft(Image myimage) {
        callScript(myimage, rotationCommandLeft, false);
    }

    public void deleteSelection() {
        for (Image image : allImages) {
            if (image.isSelected()) {
                callScript(image, deletionCommand, true);
            }
        }
    }

    public void rotateSelectionRight() {
        for (Image image : allImages) {
            if (image.isSelected()) {
                callScript(image, rotationCommandRight, false);
            }
        }
    }

    public void rotateSelectionLeft() {
        for (Image image : allImages) {
            if (image.isSelected()) {
                callScript(image, rotationCommandLeft, false);
            }
        }
    }

    public void callScript(Image myimage, String rotationCommand, boolean selectOtherImage) {
        int myindex = getImageIndex();
        if (selectOtherImage && myindex == allImages.indexOf(myimage)) {
            myindex--;
        }
        String command = rotationCommand.replace("IMAGE_FILE", imageFolderName + myimage.getImageName());
        command = command.replace("IMAGE_FOLDER", imageFolderName);
        logger.debug(command);

        try {
            Process process = Runtime.getRuntime().exec(command);
            int result = process.waitFor();
            if (result != 0) {
                logger.debug("A problem occured while calling command '" + command + "'. Error code was " + result);
            }
        } catch (IOException e) {
            logger.error("IOException in rotate()", e);
            Helper.setFehlerMeldung("Aborted Command '" + command + "' in callScript()!");
        } catch (InterruptedException e) {
            logger.error("InterruptedException in callScript()", e);
            Helper.setFehlerMeldung("Command '" + command + "' is interrupted in callScript()!");
        }

        allImages = new ArrayList<Image>();
        initialize(this.step, "");

        setImageIndex(myindex);
    }

    public void selectAllImages() {
        for (Image image : allImages) {
            image.setSelected(true);
        }
    }

    public void unselectAllImages() {
        for (Image image : allImages) {
            image.setSelected(false);
        }
    }

    public void downloadSelectedImages() {

        BufferedInputStream buf = null;

        try {
            Path tempfile = Files.createTempFile(step.getProzess().getTitel(), ".zip");

            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempfile.toFile()));

            for (Image image : allImages) {
                if (image.isSelected()) {
                    Path currentImagePath = Paths.get(imageFolderName, image.getImageName());
                    FileInputStream in = new FileInputStream(currentImagePath.toFile());
                    out.putNextEntry(new ZipEntry(image.getImageName()));
                    byte[] b = new byte[1024];
                    int count;

                    while ((count = in.read(b)) > 0) {
                        out.write(b, 0, count);
                    }
                    in.close();
                }
            }
            out.close();

            FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
            ExternalContext ec = facesContext.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/zip");
            ec.setResponseContentLength((int) Files.size(tempfile));

            ec.setResponseHeader("Content-Disposition", "attachment; filename=" + step.getProzess().getTitel() + ".zip");
            OutputStream responseOutputStream = ec.getResponseOutputStream();

            FileInputStream input = new FileInputStream(tempfile.toString());
            buf = new BufferedInputStream(input);
            int readBytes = 0;

            //read from the file; write to the ServletOutputStream
            while ((readBytes = buf.read()) != -1) {
                responseOutputStream.write(readBytes);
            }
            responseOutputStream.flush();
            responseOutputStream.close();
            facesContext.responseComplete();
        } catch (IOException e) {
            logger.error(e);
        } finally {
            if (buf != null) {
                try {
                    buf.close();
                } catch (IOException e) {
                    logger.error(e);
                }
            }
        }
    }
}

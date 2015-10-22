package de.intranda.goobi.plugins;

import java.awt.Dimension;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import de.intranda.goobi.imageview.ImageLevel;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibImageException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ImageManagerException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ImageManipulatorException;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageManager;
import de.unigoettingen.sub.commons.contentlib.imagelib.JpegInterpreter;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class ImageQAPlugin implements IStepPlugin {

    private static final Logger logger = Logger.getLogger(ImageQAPlugin.class);
    private Step step;

    private static final String PLUGIN_NAME = "intranda_step_imageQA";

    private int NUMBER_OF_IMAGES_PER_PAGE = 10;
    private int THUMBNAIL_SIZE_IN_PIXEL = 200;
    //    private int IMAGE_SIZE_IN_PIXEL = 800;

    private int pageNo = 0;

    private int imageIndex = 0;

    private String imageFolderName = "";

    private List<Image> allImages = new ArrayList<Image>();

    private Image image = null;
    private Dimension imageSize = null;
    private List<String> imageSizes;

    private ExecutorService executor;

    @Override
    public void initialize(Step step, String returnPath) {

        NUMBER_OF_IMAGES_PER_PAGE = ConfigPlugins.getPluginConfig(this).getInt("numberOfImagesPerPage", 50);
        THUMBNAIL_SIZE_IN_PIXEL = ConfigPlugins.getPluginConfig(this).getInt("thumbnailsize", 200);
        //        IMAGE_SIZE_IN_PIXEL = ConfigPlugins.getPluginConfig(this).getInt("imagesize", 800);
        imageSizes = ConfigPlugins.getPluginConfig(this).getList("imagesize");
        executor = Executors.newFixedThreadPool(imageSizes.size());
        this.step = step;
        try {
            if (ConfigPlugins.getPluginConfig(this).getBoolean("useOrigFolder", false)) {
                imageFolderName = step.getProzess().getImagesOrigDirectory(false);
            } else {
                imageFolderName = step.getProzess().getImagesTifDirectory(false);
            }
            File folder = new File(imageFolderName);
            if (folder.exists()) {
                String[] imageNameArray = folder.list();
                if (imageNameArray != null && imageNameArray.length > 0) {
                    List<String> imageNameList = Arrays.asList(imageNameArray);
                    Collections.sort(imageNameList);
                    int order = 1;
                    for (String imagename : imageNameList) {
                        Image currentImage = new Image(imagename, order++, "", imagename);
                        allImages.add(currentImage);
                    }
                    setImageIndex(0);
                }
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

        //        String myPfad = ConfigurationHelper.getTempImagesPathAsCompleteDirectory();
        //
        //        FacesContext context = FacesContext.getCurrentInstance();
        //        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        //        String mySession = session.getId() + "_" + currentImage.getImageName() + ".png";
        //        try {
        //            scaleFile(imageFolderName + currentImage.getImageName(), myPfad + mySession, THUMBNAIL_SIZE_IN_PIXEL);
        //        } catch (ContentLibImageException | IOException e) {
        //            logger.error(e);
        //        }
        //
        StringBuilder url = new StringBuilder();

        url.append("/cs").append("?action=").append("image").append("&format=").append("png").append("&sourcepath=").append("file://"
                + imageFolderName + currentImage.getImageName()).append("&width=").append(THUMBNAIL_SIZE_IN_PIXEL).append("&height=").append(
                        THUMBNAIL_SIZE_IN_PIXEL);

        currentImage.setThumbnailUrl(url.toString());

    }

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
    
    private boolean allImagesFinished(List<Future<File>> createdFiles) {
        for (Future<File> future : createdFiles) {
            try {
                if(!future.isDone() || future.get() == null) {
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
                if(future.isDone() && future.get() != null) {
                    return true;
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }
        return false;
    }

    private File scaleToSize(ImageManager im, Dimension dim, String filename, boolean overwrite) throws ImageManipulatorException, FileNotFoundException,
            ImageManagerException, IOException {
        File outputFile = new File(filename);
        if(!overwrite && outputFile.isFile()) {
            return outputFile;
        }
        try (FileOutputStream outputFileStream = new FileOutputStream(outputFile);) {
            RenderedImage ri = im.scaleImageByPixel(dim, ImageManager.SCALE_TO_BOX, 0);
            JpegInterpreter pi = new JpegInterpreter(ri);
            pi.writeToStream(null, outputFileStream);
            outputFileStream.close();
            logger.debug("Written file " + outputFile);
            return outputFile;
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

    @Override
    public String getDescription() {
        return PLUGIN_NAME;
    }

    @Override
    public String cancel() {
        return "/" + getTheme() + "/task_edit.xhtml";
    }

    @Override
    public String finish() {
        return "/" + getTheme() + "/task_edit.xhtml";
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public Step getStep() {
        return step;
    }

    public List<Image> getAllImages() {
        return allImages;
    }

    public int getImageIndex() {
        return imageIndex;
    }

    public void setImageIndex(int imageIndex) {

        this.imageIndex = imageIndex;
        if (this.imageIndex < 0) {
            this.imageIndex = 0;
        }
        if (this.imageIndex >= getSizeOfImageList()) {
            this.imageIndex = getSizeOfImageList() - 1;
        }
        setImage(allImages.get(this.imageIndex));
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

    public Image getImage() {
        return image;
    }

    public List<ImageLevel> getImageLevels() {
        List<ImageLevel> levels = new ArrayList<ImageLevel>();
        for (String sizeString : imageSizes) {
            int size = Integer.parseInt(sizeString);
            int width = 0;
            int height = 0;
            if (getImageSideRatio() > 1) {
                height = size;
                width = (int) (size / getImageSideRatio());
            } else {
                width = size;
                height = (int) (size * getImageSideRatio());
            }
            ImageLevel large = new ImageLevel(getImageUrl(image, sizeString), width, height);
            levels.add(large);
        }
        return levels;
    }

    public int getImageWidth() {
        if (imageSize != null) {
            return (int) imageSize.getWidth();
        } else {
            logger.error("Must set image before querying image size");
            return 0;
        }
    }

    public int getImageHeight() {
        if (imageSize != null) {
            return (int) imageSize.getHeight();
        } else {
            logger.error("Must set image before querying image size");
            return 0;
        }
    }

    /**
     * @return imageHeight/imageWidth
     */
    public double getImageSideRatio() {
        if (imageSize != null) {
            return imageSize.getHeight() / imageSize.getWidth();
        } else {
            logger.error("Must set image before querying image size");
            return 1.0;
        }
    }

    private String getImageUrl(Image image, String size) {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String currentImageURL = session.getServletContext().getContextPath() + ConfigurationHelper.getTempImagesPath() + session.getId() + "_"
                + image.getImageName() + "_large_" + size + ".jpg";
        return currentImageURL;
    }

    private String getImagePath(Image image) {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String path = ConfigurationHelper.getTempImagesPathAsCompleteDirectory() + session.getId() + "_" + image.getImageName() + "_large" + ".jpg";
        return path;
    }

    public void setImage(final Image image) {
        this.image = image;
        logger.debug("Creating scaled images for " + image.getImageName());
        try {
            final String currentImageURL = getImagePath(image);
            if (currentImageURL != null) {
                this.imageSize = scaleFile(imageFolderName + image.getImageName(), currentImageURL, imageSizes);
            }
        } catch (ContentLibImageException | IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
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

    public void setTxtMoveTo(int neueSeite) {
        if ((this.pageNo != neueSeite - 1) && neueSeite > 0 && neueSeite <= getLastPageNumber() + 1) {
            this.pageNo = neueSeite - 1;
            getPaginatorList();
        }
    }

    public int getTxtMoveTo() {
        return this.pageNo + 1;
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
}

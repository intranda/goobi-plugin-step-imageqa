package de.intranda.goobi.plugins;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 * 
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 */
import java.awt.Dimension;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import com.google.gson.Gson;

import de.intranda.goobi.NamePart;
import de.intranda.goobi.SelectableImage;
import de.intranda.goobi.SelectableImageForJSON;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.FilesystemHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.S3FileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.metadaten.Image;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibImageException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ImageManagerException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ImageManipulatorException;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageManager;
import de.unigoettingen.sub.commons.contentlib.imagelib.JpegInterpreter;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@Data
@PluginImplementation
@Log4j
public class ImageQAPlugin implements IStepPlugin {

    private static final DecimalFormat PAGENUMBERFORMAT = new DecimalFormat("0000");
    private static final DecimalFormat FILENUMBERFORMAT = new DecimalFormat("00000000");
    private static final Gson gson = new Gson();

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
    private boolean allowSelectionAll = false;
    private boolean allowDownload = false;
    private boolean allowDownloadAsPdf = false;
    private boolean allowTaskFinishButtons = true;
    private boolean useTiles = false;
    private boolean useTilesFullscreen = false;
    private String rotationCommandLeft = "";
    private String rotationCommandRight = "";
    private String deletionCommand = "";
    boolean askForConfirmation = true;

    private int pageNo = 0;

    private int imageIndex = 0;

    private String imageFolderName = "";

    private List<SelectableImage> allImages = new ArrayList<>();

    private Image image = null;
    private List<String> imageSizes = new ArrayList<>();
    private String tileSize;
    private List<String> scaleFactors = new ArrayList<>();
    private ExecutorService executor;

    private String returnPath;
    // ocr display variables
    private boolean displayOcrButton = false;
    private String ocrDir = "";
    private boolean ocrExists = false;
    private boolean displayOCR = false;
    private String ocrText = "";

    private boolean pagesRTL;
    private boolean useJSFullscreen;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        String projectName = step.getProzess().getProjekt().getTitel();

        XMLConfiguration xmlConfig = ConfigPlugins.getPluginConfig(PLUGIN_NAME);
        xmlConfig.setExpressionEngine(new XPathExpressionEngine());
        xmlConfig.setReloadingStrategy(new FileChangedReloadingStrategy());

        SubnodeConfiguration myconfig = null;

        // order of configuration is:
        // 1.) project name and step name matches
        // 2.) step name matches and project is *
        // 3.) project name matches and step name is *
        // 4.) project name and step name are *
        try {
            myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '" + step.getTitel() + "']");
        } catch (IllegalArgumentException e) {
            try {
                myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '" + step.getTitel() + "']");
            } catch (IllegalArgumentException e1) {
                try {
                    myconfig = xmlConfig.configurationAt("//config[./project = '" + projectName + "'][./step = '*']");
                } catch (IllegalArgumentException e2) {
                    myconfig = xmlConfig.configurationAt("//config[./project = '*'][./step = '*']");
                }
            }
        }

        initConfig(myconfig);
        try {
            String imageFolder;
            if (myconfig.getBoolean("useOrigFolder", false)) {
                imageFolder = step.getProzess().getImagesOrigDirectory(false);
            } else {
                imageFolder = step.getProzess().getImagesTifDirectory(false);
            }
            initImageList(myconfig, imageFolder);
            this.useJSFullscreen = myconfig.getBoolean("useJSFullscreen");
        } catch (SwapException | DAOException | IOException | InterruptedException e) {
            log.error(e);
        }
    }

    /**
     * @param myconfig
     * @param imageFolder
     */
    public void initImageList(SubnodeConfiguration myconfig, String imageFolder) {
        this.imageFolderName = imageFolder;
        Path path = Paths.get(imageFolderName);
        List<NamePart> nameParts = initImageNameParts(myconfig);
        if (StorageProvider.getInstance().isFileExists(path)) {
            List<String> imageNameList = StorageProvider.getInstance().list(imageFolderName, NIOFileUtils.imageOrObjectNameFilter);
            int order = 1;
            for (String imagename : imageNameList) {
                SelectableImage currentImage;
                Path imagePath = Paths.get(imageFolderName, imagename);
                try {
                    currentImage = new SelectableImage(getStep().getProzess(), imageFolderName, imagename, order, THUMBNAIL_SIZE_IN_PIXEL);
                    //                    currentImage = new SelectableImage(imagePath, order, THUMBNAIL_SIZE_IN_PIXEL);
                    currentImage.initNameParts(nameParts);
                    allImages.add(currentImage);
                    order++;
                } catch (IOException | InterruptedException | SwapException | DAOException e) {
                    log.error("Error initializing image " + imagename, e);
                }
            }
            setImageIndex(0);
        }

        //check pages RTL:
        try {
            this.pagesRTL = readPagesRTLFromXML();

        } catch (Exception e) {
            //by default, false
        }

    }

    public boolean readPagesRTLFromXML()
            throws ReadException, PreferencesException, WriteException, IOException, InterruptedException, SwapException, DAOException {
        org.goobi.beans.Process myProzess = this.getStep().getProzess();

        Fileformat gdzfile = myProzess.readMetadataFile();
        if (gdzfile == null) {
            return false;
        }

        DigitalDocument mydocument = gdzfile.getDigitalDocument();

        DocStruct logicalTopstruct = mydocument.getLogicalDocStruct();
        if (logicalTopstruct.getType().isAnchor()) {
            logicalTopstruct = logicalTopstruct.getAllChildren().get(0);
        }

        if (logicalTopstruct.getAllMetadata() != null) {

            List<Metadata> lstMetadata = logicalTopstruct.getAllMetadata();
            for (Metadata md : lstMetadata) {
                if (md.getType().getName().equals("_directionRTL")) {
                    try {
                        boolean value = Boolean.valueOf(md.getValue());
                        return value;
                    } catch (Exception e) {

                    }
                }
            }
        }

        //default:
        return false;
    }

    /**
     * reads configfile and sets object variables accordingly, sets defaults for some settings if no value is specified
     * 
     * @param myconfig SubnodeConfiguration object of the config file
     */
    public void initConfig(SubnodeConfiguration myconfig) {
        allowDeletion = myconfig.getBoolean("allowDeletion", false);
        allowRotation = myconfig.getBoolean("allowRotation", false);
        allowRenaming = myconfig.getBoolean("allowRenaming", false);
        allowSelection = myconfig.getBoolean("allowSelection", false);
        allowSelectionAll = myconfig.getBoolean("allowSelectionAll", false);
        allowDownload = myconfig.getBoolean("allowDownload", false);
        allowDownloadAsPdf = myconfig.getBoolean("allowDownloadAsPdf", false);
        allowTaskFinishButtons = myconfig.getBoolean("allowTaskFinishButtons", true);
        deletionCommand = myconfig.getString("deletionCommand", "");
        rotationCommandLeft = myconfig.getString("rotationCommands/left", "-");
        rotationCommandRight = myconfig.getString("rotationCommands/right", "-");

        NUMBER_OF_IMAGES_PER_PAGE = myconfig.getInt("numberOfImagesPerPage", 50);
        THUMBNAIL_SIZE_IN_PIXEL = myconfig.getInt("thumbnailsize", 200);
        THUMBNAIL_FORMAT = myconfig.getString("thumbnailFormat", "png");
        MAINIMAGE_FORMAT = myconfig.getString("mainImageFormat", "jpg");
        imageSizes = myconfig.getList("imagesize");
        if (imageSizes == null || imageSizes.isEmpty()) {
            imageSizes = new ArrayList<>();
        }
        tileSize = myconfig.getString("tileSize");
        if (tileSize == null) {
            tileSize = "";
        }
        /* load scale factors, set default of none found */
        scaleFactors = myconfig.getList("scaleFactors");
        if (scaleFactors == null || scaleFactors.isEmpty()) {
            scaleFactors = new ArrayList<>();
            scaleFactors.add("1");
            scaleFactors.add("32");
        }
        useTiles = myconfig.getBoolean("useTiles", false);
        useTilesFullscreen = myconfig.getBoolean("useTilesFullscreen", true);
        executor = Executors.newFixedThreadPool(Math.max(1, imageSizes.size()));
        displayOcrButton = myconfig.getBoolean("displayocr", true);
        // only display button if it is both configured and there is an ocr folder for
        // this process
        if (displayOcrButton) {
            try {
                if (!StorageProvider.getInstance().isDirectory(Paths.get(step.getProzess().getOcrDirectory()))) {
                    displayOcrButton = false;
                }
            } catch (SwapException | DAOException | IOException | InterruptedException e) {
                log.debug("OCR folder could not be accessed", e);
            }
        }
    }

    /**
     * builds String to apply settings for tile-size and scale Factors from the config file
     * 
     * @return String that sets the tile size according to the config file
     */
    public String getTileSize() {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(tileSize)) {
            sb.append("[{\"width\":").append(tileSize).append(",\"scaleFactors\":[");
            for (String factor : scaleFactors) {
                sb.append(factor).append(',');
            }
            if (sb.toString().endsWith(",")) {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append("]}]");
        } else {
            sb.append("[]");
        }
        return sb.toString();
    }

    /**
     * builds String to apply settings for display size from the config file
     * 
     * @return returns String that sets the display size according to the config
     */
    public String getDisplaySizes() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (String size : imageSizes) {
            sb.append("{\"width\":").append(size).append("},");
        }
        if (sb.toString().endsWith(",")) {
            sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");
        return sb.toString();

    }

    private List<NamePart> initImageNameParts(HierarchicalConfiguration config) {
        try {
            HierarchicalConfiguration patternList = config.configurationAt("renamingPattern");
            List<HierarchicalConfiguration> fields = patternList.configurationsAt("field");
            List<NamePart> nameParts = new ArrayList<>();
            for (HierarchicalConfiguration fieldConfig : fields) {
                nameParts.add(new NamePart(fieldConfig, getStep()));
            }
            return nameParts;
        } catch (Throwable e) {
            return Collections.singletonList(new NamePart(""));
        }
    }

    /**
     * TODO document
     * 
     */

    public List<SelectableImage> getPaginatorList() {
        List<SelectableImage> subList = new ArrayList<>();
        if (allImages.size() > (pageNo * NUMBER_OF_IMAGES_PER_PAGE) + NUMBER_OF_IMAGES_PER_PAGE) {
            subList = allImages.subList(pageNo * NUMBER_OF_IMAGES_PER_PAGE, (pageNo * NUMBER_OF_IMAGES_PER_PAGE) + NUMBER_OF_IMAGES_PER_PAGE);
        } else {
            subList = allImages.subList(pageNo * NUMBER_OF_IMAGES_PER_PAGE, allImages.size());
        }
        return subList;
    }

    public String getAllImagesJSON() {
        List<SelectableImageForJSON> images = this.allImages.stream()
                .map(SelectableImageForJSON::new)
                .collect(Collectors.toList());
        return gson.toJson(images);
    }

    public Image getImage() {
        return image;
    }

    @SuppressWarnings("unused")
    private Dimension scaleFile(String inFileName, String outFileName, List<String> sizes) throws IOException, ContentLibImageException {

        final ImageManager im = new ImageManager(new File(inFileName).toURI());
        Dimension originalImageSize = new Dimension(im.getMyInterpreter().getWidth(), im.getMyInterpreter().getHeight());
        String outputFilePath = FilenameUtils.getFullPath(outFileName);
        String outputFileBasename = FilenameUtils.getBaseName(outFileName);
        String outputFileSuffix = FilenameUtils.getExtension(outFileName);
        List<Future<Path>> createdFiles = new ArrayList<>();
        for (String sizeString : sizes) {
            int size = Integer.parseInt(sizeString);
            final Dimension dim = new Dimension();
            dim.setSize(size, size);
            final String filename = outputFilePath + outputFileBasename + "_" + size + "." + outputFileSuffix;
            createdFiles.add(executor.submit(new Callable<Path>() {

                @Override
                public Path call() throws Exception {
                    return scaleToSize(im, dim, filename, false);
                }
            }));
        }
        while (!oneImageFinished(createdFiles)) {

        }
        log.debug("First image finished generation");
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

    private boolean oneImageFinished(List<Future<Path>> createdFiles) {
        for (Future<Path> future : createdFiles) {
            try {
                if (future.isDone() && future.get() != null) {
                    return true;
                }
            } catch (InterruptedException | ExecutionException e) {
            }
        }
        return false;
    }

    private Path scaleToSize(ImageManager im, Dimension dim, String filename, boolean overwrite)
            throws ImageManipulatorException, FileNotFoundException, ImageManagerException, IOException, ContentLibException {
        Path outputFile = Paths.get(filename);
        if (!overwrite && StorageProvider.getInstance().isFileExists(outputFile)) {
            return outputFile;
        }
        Path tempFile = Files.createTempFile("resize_tmp", ".tif");
        try (OutputStream outputFileStream = Files.newOutputStream(tempFile)) {
            RenderedImage ri = im.scaleImageByPixel(dim, ImageManager.SCALE_TO_BOX, 0);
            try (JpegInterpreter pi = new JpegInterpreter(ri)) {
                pi.writeToStream(null, outputFileStream);
                outputFileStream.close();
                log.debug("Written file " + outputFile);
            }
        }
        try (InputStream in = Files.newInputStream(tempFile)) {
            StorageProvider.getInstance().uploadFile(in, outputFile);
        }
        Files.delete(tempFile);
        return outputFile;
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

    /**
     * 
     * Adjusts the variable imageIndex by the passed int, ensureing it does not go out of bounds in the process if displaOCR is set it also updates
     * the OCRtext
     * 
     */
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
        if (displayOCR) {
            updateOCR();
        }
    }

    /**
     * 
     * tries to retrieve OCR text for current image and puts it in ocrText, then flips the switch ocrExists
     * 
     */
    public void updateOCR() {
        String filename = this.image.getImageName();
        filename = FilenameUtils.removeExtension(filename);
        ocrText = "";
        ocrText = FilesystemHelper.getOcrFileContent(step.getProzess(), filename);
        if (ocrText == "") {
            ocrExists = false;
        } else {
            ocrExists = true;
        }
    }

    public String getFlowDir() {

        return this.pagesRTL ? "rtl" : "ltr";
    }

    /**
     * 
     * @return String url of currently focussed image
     * 
     */
    public String getBild() {
        if (image == null) {
            return null;
        } else {
            FacesContext context = FacesContextHelper.getCurrentFacesContext();
            HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
            String currentImageURL =
                    context.getExternalContext().getRequestContextPath() + ConfigurationHelper.getTempImagesPath() + session.getId() + "_"
                            + image.getImageName() + "_large_" + ".jpg";
            return currentImageURL.replaceAll("\\\\", "/");
        }
    }

    //Blätter nach rechts:
    public void imageRight() {

        if (pagesRTL) {
            setImageIndex(imageIndex - 1);
        } else {
            setImageIndex(imageIndex + 1);
        }
    }

    //blätter nach links
    public void imageLeft() {

        if (pagesRTL) {
            setImageIndex(imageIndex + 1);
        } else {
            setImageIndex(imageIndex - 1);
        }
    }

    //blätter ganz nach links
    public void imageLeftmost() {

        if (pagesRTL) {
            setImageIndex(getSizeOfImageList() - 1);
        } else {
            setImageIndex(0);
        }
    }

    //blätter ganz nach rechts
    public void imageRightmost() {

        if (pagesRTL) {
            setImageIndex(0);
        } else {
            setImageIndex(getSizeOfImageList() - 1);
        }
    }

    public int getImageWidth() {
        if (image == null) {
            log.error("Must set image before querying image size");
            return 0;
        }
        return image.getSize().width;
    }

    public int getImageHeight() {
        if (image == null) {
            log.error("Must set image before querying image size");
            return 0;
        }
        return image.getSize().height;
    }

    @SuppressWarnings("unused")
    private String getImageUrl(Image image, String sizeString) {
        try {
            int size = Integer.parseInt(sizeString);
            image.createThumbnailUrls(size);
            return image.getThumbnailUrl();
        } catch (NullPointerException | NumberFormatException e) {
            log.error("Unable to set size of image ", e);
        }
        return image.getObjectUrl();
    }

    @SuppressWarnings("unused")
    private String getImagePath(Image image) {
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String path = ConfigurationHelper.getTempImagesPathAsCompleteDirectory() + session.getId() + "_" + image.getImageName() + "_large" + ".jpg";
        return path;
    }

    public String cmdMoveFirst() {
        if (pagesRTL) {
            if (this.pageNo != getLastPageNumber()) {
                this.pageNo = getLastPageNumber();
                getPaginatorList();
            }
        } else {
            if (this.pageNo != 0) {
                this.pageNo = 0;
                getPaginatorList();
            }
        }
        return "";
    }

    public String cmdMovePrevious() {
        if (pagesRTL) {
            if (!isLastPage()) {
                this.pageNo++;
                getPaginatorList();
            }
        } else {
            if (!isFirstPage()) {
                this.pageNo--;
                getPaginatorList();
            }
        }
        return "";
    }

    public String cmdMoveNext() {
        if (pagesRTL) {
            if (!isFirstPage()) {
                this.pageNo--;
                getPaginatorList();
            }
        } else {
            if (!isLastPage()) {
                this.pageNo++;
                getPaginatorList();
            }
        }
        return "";

    }

    public String cmdMoveLast() {
        if (pagesRTL) {
            if (this.pageNo != 0) {
                this.pageNo = 0;
                getPaginatorList();
            }
        } else {
            if (this.pageNo != getLastPageNumber()) {
                this.pageNo = getLastPageNumber();
                getPaginatorList();
            }
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

    public String renameImages(SelectableImage myimage) {

        System.out.println("Dateien werden jetzt umbenannt auf der Basis von: " + myimage.getNameParts());

        int imageIndex = getAllImages().indexOf(myimage);
        Iterator<SelectableImage> iterator = getAllImages().listIterator();

        int pageCounter = 0;
        String myCombinedName = myimage.getCombinedName();
        String myPreviousCombinedName = myimage.getCombinedNameFromFilename();
        boolean imageNameFound = false;
        Map<Path, Path> renamingMap = new LinkedHashMap<>();
        while (iterator.hasNext()) {
            SelectableImage currentImage = iterator.next();
            int currentImageIndex = getAllImages().indexOf(currentImage);
            String suffix = currentImage.getImageName().substring(currentImage.getImageName().lastIndexOf("."));
            String currentCombinedName = currentImage.getCombinedName();

            if (currentImageIndex >= imageIndex && (myPreviousCombinedName.equals(currentCombinedName) || myCombinedName.equals(currentCombinedName)
                    || currentImage.isNotYetNamed())) {
                imageNameFound = true;
                currentImage.setNameParts(myimage.getNameParts());
                String currentPageCounter = PAGENUMBERFORMAT.format(++pageCounter);
                currentImage.setPageCounterLabel(currentPageCounter);
                String newFilename = currentImage.getNamePrefix() + "_" + myCombinedName + currentPageCounter + suffix;
                Path imageFile = Paths.get(imageFolderName, currentImage.getImageName());
                Path newImageFile = Paths.get(imageFolderName, newFilename);
                renamingMap.put(imageFile, newImageFile);
                currentImage.setImageName(newFilename);
            } else if (myCombinedName.equals(currentCombinedName)) {
                pageCounter++;
            } else if (imageNameFound) {
                break;
            }
        }

        boolean renamed = true;
        while (renamed && !renamingMap.isEmpty()) {
            List<Path> toBeRenamedList = new ArrayList<>(renamingMap.keySet());
            Collections.reverse(toBeRenamedList);
            Iterator<Path> iter = toBeRenamedList.iterator();
            renamed = false;
            while (iter.hasNext()) {
                Path currentFile = iter.next();
                Path newFile = renamingMap.get(currentFile);
                if (StorageProvider.getInstance().isFileExists(newFile) && !newFile.equals(currentFile)) {
                    // logger.error("Trying to rename " + currentFile.getName() + " to " +
                    // newFile.getName() + ". But file already exists");
                    // Helper.setFehlerMeldung("Trying to rename " + currentFile.getName() + " to "
                    // + newFile.getName() + ". But file already exists");
                    // break;
                } else {
                    try {
                        StorageProvider.getInstance().move(currentFile, newFile);
                        renamed = true;
                        renamingMap.remove(currentFile);
                    } catch (IOException e) {
                        log.error(e);
                    }
                }
            }
        }
        if (!renamingMap.isEmpty()) {
            Helper.setFehlerMeldung("Error renaming files - file " + renamingMap.keySet().iterator().next() + " could not be renamed to "
                    + renamingMap.get(renamingMap.keySet().iterator().next()));
        }

        allImages = new ArrayList<>();

        initialize(this.step, returnPath);

        setImageIndex(imageIndex);
        return "";
    }

    public void deleteImage(Image myimage) {
        if (deletionCommand == null || deletionCommand.length() == 0) {
            deleteImageViaJava(myimage);
        } else {
            callScript(myimage, deletionCommand, true);
        }
    }

    public void rotateRight(Image myimage) {
        callScript(myimage, rotationCommandRight, false);
    }

    public void rotateLeft(Image myimage) {
        callScript(myimage, rotationCommandLeft, false);
    }

    public void deleteSelection() {
        Iterator<SelectableImage> iterator = allImages.iterator();
        while (iterator.hasNext()) {
            SelectableImage image = iterator.next();
            if (image.isSelected()) {
                if (deletionCommand == null || deletionCommand.length() == 0) {
                    deleteImageViaJava(image);
                } else {
                    callScript(image, deletionCommand, true);
                }
            }
        }
    }

    public void rotateSelectionRight() {
        for (SelectableImage image : allImages) {
            if (image.isSelected()) {
                callScript(image, rotationCommandRight, false);
            }
        }
    }

    public void rotateSelectionLeft() {
        for (SelectableImage image : allImages) {
            if (image.isSelected()) {
                callScript(image, rotationCommandLeft, false);
            }
        }
    }

    public void deleteImageViaJava(Image myimage) {
        int myindex = getImageIndex();
        if (myindex == allImages.indexOf(myimage)) {
            myindex--;
        }

        try {
            StorageProvider.getInstance().deleteFile(myimage.getImagePath());
        } catch (IOException e) {
            Helper.setFehlerMeldung("Error while deleting file " + myimage.getImagePath() + ": " + e.getMessage());
        }

        allImages = new ArrayList<>();
        initialize(this.step, returnPath);

        setImageIndex(myindex);
    }

    public void callScript(Image myimage, String rotationCommand, boolean selectOtherImage) {
        int myindex = getImageIndex();
        if (selectOtherImage && myindex == allImages.indexOf(myimage)) {
            myindex--;
        }
        ConfigurationHelper config = ConfigurationHelper.getInstance();
        String imageURI = imageFolderName + myimage.getImageName();
        String imageFolderURI = imageFolderName;
        if (config.useS3()) {
            imageURI = S3FileUtils.string2Key(imageURI);
            imageFolderURI = S3FileUtils.string2Prefix(imageFolderURI);
        }
        String command = rotationCommand.replace("IMAGE_FILE", imageURI);
        command = command.replace("IMAGE_FOLDER", imageFolderURI);
        log.debug(command);

        try {
            Process process = Runtime.getRuntime().exec(command);
            int result = process.waitFor();
            if (result != 0) {
                log.error("A problem occured while calling command '" + command + "'. The error code was " + result);
                Helper.setFehlerMeldung("A problem occured while calling command '" + command + "'. The error code was " + result);
            }
        } catch (IOException e) {
            log.error("IOException in rotate()", e);
            Helper.setFehlerMeldung("Aborted Command '" + command + "' in callScript()!");
        } catch (InterruptedException e) {
            log.error("InterruptedException in callScript()", e);
            Helper.setFehlerMeldung("Command '" + command + "' is interrupted in callScript()!");
        }

        allImages = new ArrayList<>();
        initialize(this.step, returnPath);

        setImageIndex(myindex);
    }

    public void selectAllImages() {
        for (SelectableImage image : allImages) {
            image.setSelected(true);
        }
    }

    public void unselectAllImages() {
        for (SelectableImage image : allImages) {
            image.setSelected(false);
        }
    }

    public void downloadSelectedImages() {

        try {
            FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
            ExternalContext ec = facesContext.getExternalContext();
            ec.responseReset();
            ec.setResponseContentType("application/zip");

            ec.setResponseHeader("Content-Disposition", "attachment; filename=" + step.getProzess().getTitel() + ".zip");
            OutputStream responseOutputStream = ec.getResponseOutputStream();
            ZipOutputStream out = new ZipOutputStream(responseOutputStream);

            for (SelectableImage image : allImages) {
                if (image.isSelected()) {

                    Path currentImagePath = Paths.get(imageFolderName, image.getImageName());

                    InputStream in = StorageProvider.getInstance().newInputStream(currentImagePath);
                    out.putNextEntry(new ZipEntry(image.getImageName()));
                    byte[] b = new byte[1024];
                    int count;

                    while ((count = in.read(b)) > 0) {
                        out.write(b, 0, count);
                    }
                    in.close();
                }
            }
            out.flush();
            out.close();

            facesContext.responseComplete();
        } catch (IOException e) {
            log.error(e);
        } finally {
        }
    }

    public void downloadSelectedImagesAsPdf() throws IOException, DAOException, SwapException, InterruptedException {

        // prepare contentserver URL
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        String contentServerUrl = ConfigurationHelper.getInstance().getGoobiContentServerUrl();
        if (contentServerUrl == null || contentServerUrl.length() == 0) {
            HttpServletRequest req = (HttpServletRequest) context.getExternalContext().getRequest();
            String fullpath = req.getRequestURI().toString();
            String servletpath = context.getExternalContext().getRequestServletPath();
            String myBasisUrl = fullpath.substring(0, fullpath.indexOf(servletpath));
            contentServerUrl = myBasisUrl + "/cs/cs?action=pdf&images=";
        }

        // put all selected images into a URL
        String url = "";
        for (SelectableImage image : allImages) {
            if (image.isSelected()) {

                Path currentImagePath = Paths.get(imageFolderName, image.getImageName());
                url = url + StorageProvider.getInstance().getURI(currentImagePath) + "$";
            }
        }

        // generate the final URL
        String imageString = url.substring(0, url.length() - 1);
        String targetFileName = "&targetFileName=" + step.getProzess().getTitel() + ".pdf";
        URL goobiContentServerUrl = new URL(contentServerUrl + imageString + targetFileName);

        // generate the pdf and deliver it as download
        if (!context.getResponseComplete()) {
            HttpServletResponse response = (HttpServletResponse) context.getExternalContext().getResponse();
            String fileName = step.getProzess().getTitel() + ".pdf";
            ServletContext servletContext = (ServletContext) context.getExternalContext().getContext();
            String contentType = servletContext.getMimeType(fileName);
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
            response.sendRedirect(goobiContentServerUrl.toString());
            context.responseComplete();
        }
    }

    private String getImageFolderShort() {
        String imageFolder = Paths.get(imageFolderName).getFileName().toString();
        if (imageFolder.startsWith("master_") || imageFolder.startsWith("orig_")) {
            return "master";
        } else if (imageFolder.contains("_")) {
            return imageFolder.substring(imageFolder.lastIndexOf("_") + 1);
        } else {
            return imageFolder;
        }
    }

    public String getImageUrl(Image image) {
        return image.getObjectUrl();
    }

    public String getImageWebApiToken() {
        return ConfigPlugins.getPluginConfig(PLUGIN_NAME).getString("imageWebApiToken", "test");
    }

    /**
     * flips the boolean displayOCR used to switch the ocr-display on or off, aditionally update the ocrtext if it will be displayed
     */
    public void toggleOCR() {
        displayOCR = !displayOCR;
        if (displayOCR) {
            updateOCR();
        }
    }
}

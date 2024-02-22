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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.goobi.beans.ImageComment;
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
import de.sub.goobi.forms.HelperForm;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.FilesystemHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.S3FileUtils;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.metadaten.Image;
import de.sub.goobi.metadaten.ImageCommentPropertyHelper;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibImageException;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageManager;
import de.unigoettingen.sub.commons.contentlib.imagelib.JpegInterpreter;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;

@Data
@PluginImplementation
@Log4j2
public class ImageQAPlugin implements IStepPlugin {

    private static final long serialVersionUID = 132745467429762012L;
    private static final DecimalFormat PAGENUMBERFORMAT = new DecimalFormat("0000");
    private static final Gson gson = new Gson();

    private Step step;
    private static final String PLUGIN_NAME = "intranda_step_imageQA";

    private int numberOfImagesInFullGUI = 10;
    private int numberOfImagesInPartGUI = 5;
    private int THUMBNAIL_SIZE_IN_PIXEL = 175;
    private String THUMBNAIL_FORMAT = "png";
    private String MAINIMAGE_FORMAT = "jpg";
    private boolean allowDeletion = false;
    private boolean allowFlipping = false;
    private boolean allowRotation = false;
    private boolean allowRenaming = false;
    private boolean allowSelection = false;
    private boolean allowSelectionPage = false;
    private boolean allowSelectionAll = false;
    private boolean allowDownload = false;
    private boolean allowDownloadAsPdf = false;
    private boolean allowTaskFinishButtons = true;
    private boolean useTiles = false;
    private boolean useTilesFullscreen = false;
    private List<String> flippingCommandHorizontal = null;
    private List<String> flippingCommandVertical = null;
    private List<String> rotationCommandLeft = null;
    private List<String> rotationCommandRight = null;
    private List<String> deletionCommand = null;
    boolean askForConfirmation = true;

    private boolean persistentRotationAboveBigImage = false;

    private String guiType;

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
    private String displayMode = "";

    private boolean pagesRTL;
    private boolean useJSFullscreen;
    private boolean noShortcutPrefix;
    private boolean thumbnailsOnly;

    private String selectedImageFolder;
    private List<String> possibleImageFolder;

    private SubnodeConfiguration config;

    private String downloadMode = "tar"; // zip, tar or tgz

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

        config = myconfig;
        possibleImageFolder = Arrays.asList(myconfig.getStringArray("foldername"));
        if (!possibleImageFolder.isEmpty()) {
            selectedImageFolder = possibleImageFolder.get(0);
        } else {
            selectedImageFolder = "master";
        }
        String imageFolder = null;
        try {
            imageFolder = step.getProzess().getConfiguredImageFolder(selectedImageFolder);
        } catch (IOException | SwapException | DAOException e) {
            log.error(e);
        }

        initImageList(config, imageFolder);
        this.useJSFullscreen = myconfig.getBoolean("useJSFullscreen", false);
        this.noShortcutPrefix = myconfig.getBoolean("noShortcutPrefix", false);
        this.thumbnailsOnly = myconfig.getBoolean("thumbnailsOnly", false);

        this.showImageComments = ConfigurationHelper.getInstance().getMetsEditorShowImageComments();
    }

    public void changeFolder() {
        String imageFolder = null;
        try {
            imageFolder = step.getProzess().getConfiguredImageFolder(selectedImageFolder);
        } catch (IOException | SwapException | DAOException e) {
            log.error(e);
        }
        initImageList(config, imageFolder);
    }

    /**
     * @param myconfig
     * @param imageFolder
     */
    public void initImageList(SubnodeConfiguration myconfig, String imageFolder) {
        this.imageFolderName = imageFolder;
        allImages.clear();
        Path path = Paths.get(imageFolderName);
        List<NamePart> nameParts = initImageNameParts(myconfig);
        if (StorageProvider.getInstance().isFileExists(path)) {
            List<String> imageNameList = StorageProvider.getInstance().list(imageFolderName, NIOFileUtils.imageOrObjectNameFilter);
            int order = 1;
            for (String imagename : imageNameList) {
                SelectableImage currentImage;
                try {
                    currentImage = new SelectableImage(getStep().getProzess(), imageFolderName, imagename, order, THUMBNAIL_SIZE_IN_PIXEL);
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
            log.error(e);
        }

    }

    public boolean readPagesRTLFromXML()
            throws ReadException, PreferencesException, IOException, SwapException {
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
                if ("_directionRTL".equals(md.getType().getName())) {
                    try {
                        return Boolean.parseBoolean(md.getValue());
                    } catch (Exception e) {
                        // do nothing
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
        guiType = myconfig.getString("guiType", "full"); // full, part, both
        allowDeletion = myconfig.getBoolean("allowDeletion", false);
        allowFlipping = myconfig.getBoolean("allowFlipping", false);
        allowRotation = myconfig.getBoolean("allowRotation", false);
        persistentRotationAboveBigImage = myconfig.getBoolean("allowRotation/@persistentRotationAboveBigImage", false);
        allowRenaming = myconfig.getBoolean("allowRenaming", false);
        allowSelection = myconfig.getBoolean("allowSelection", false);
        allowSelectionPage = myconfig.getBoolean("allowSelectionPage", false);
        allowSelectionAll = myconfig.getBoolean("allowSelectionAll", false);
        allowDownload = myconfig.getBoolean("allowDownload", false);
        allowDownloadAsPdf = myconfig.getBoolean("allowDownloadAsPdf", false);
        allowTaskFinishButtons = myconfig.getBoolean("allowTaskFinishButtons", true);
        deletionCommand = Arrays.asList(myconfig.getStringArray("deletion/@command"));
        flippingCommandHorizontal = Arrays.asList(myconfig.getStringArray("flippingCommands/horizontal/@command"));
        flippingCommandVertical = Arrays.asList(myconfig.getStringArray("flippingCommands/vertical/@command"));
        rotationCommandLeft = Arrays.asList(myconfig.getStringArray("rotationCommands/left/@command"));
        rotationCommandRight = Arrays.asList(myconfig.getStringArray("rotationCommands/right/@command"));

        numberOfImagesInFullGUI = myconfig.getInt("numberOfImagesPerPage", 50);
        numberOfImagesInPartGUI = myconfig.getInt("numberOfImagesInPartGUI", 8);
        THUMBNAIL_SIZE_IN_PIXEL = myconfig.getInt("thumbnailsize", 200);
        THUMBNAIL_FORMAT = myconfig.getString("thumbnailFormat", "png");
        MAINIMAGE_FORMAT = myconfig.getString("mainImageFormat", "jpg");
        imageSizes = Arrays.asList(myconfig.getStringArray("imagesize"));
        if (imageSizes == null || imageSizes.isEmpty()) {
            imageSizes = new ArrayList<>();
        }
        tileSize = myconfig.getString("tileSize");
        if (tileSize == null) {
            tileSize = "";
        }
        /* load scale factors, set default of none found */
        scaleFactors = Arrays.asList(myconfig.getStringArray("scaleFactors"));
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
            } catch (SwapException | IOException e) {
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

    public String imageClick() {
        if (this.thumbnailsOnly) {
            if (this.useJSFullscreen) {
                return "ImageQAPlugin_JSFullscreen";
            }
            return "ImageQAPlugin_Fullscreen";
        }
        return "";
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
     * 
     */
    public List<SelectableImage> getPaginatorList() {
        if ("part".equals(displayMode)) {
            return getPaginatorListForPartGUI();
        } else {
            List<SelectableImage> subList = null;
            if (pageNo * numberOfImagesInFullGUI > allImages.size()) {
                pageNo = 0;
            }
            if (allImages.size() > (pageNo * numberOfImagesInFullGUI) + numberOfImagesInFullGUI) {
                subList = allImages.subList(pageNo * numberOfImagesInFullGUI, (pageNo * numberOfImagesInFullGUI) + numberOfImagesInFullGUI);
            } else {
                subList = allImages.subList(pageNo * numberOfImagesInFullGUI, allImages.size());
            }
            return subList;
        }
    }

    public List<SelectableImage> getPaginatorListForPartGUI() {
        List<SelectableImage> subList = null;
        if (allImages.size() > (pageNo * numberOfImagesInPartGUI) + numberOfImagesInPartGUI) {
            subList = allImages.subList(pageNo * numberOfImagesInPartGUI, (pageNo * numberOfImagesInPartGUI) + numberOfImagesInPartGUI);
        } else {
            subList = allImages.subList(pageNo * numberOfImagesInPartGUI, allImages.size());
        }
        return subList;
    }

    public String getAllImagesJSON() {
        List<SelectableImageForJSON> images = this.allImages.stream().map(SelectableImageForJSON::new).collect(Collectors.toList());
        return gson.toJson(images);
    }

    public Image getImage() {
        return image;
    }

    @SuppressWarnings("unused")
    private Dimension scaleFile(String inFileName, String outFileName, List<String> sizes) throws IOException, ContentLibImageException {

        try (final ImageManager im = new ImageManager(new File(inFileName).toURI())) {
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

            log.debug("First image finished generation");
            return originalImageSize;
        }

    }

    @SuppressWarnings("unused")
    private boolean allImagesFinished(List<Future<File>> createdFiles) {
        for (Future<File> future : createdFiles) {
            try {
                if (!future.isDone() || future.get() == null) {
                    return false;
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error(e);
            }
        }
        return true;
    }

    private Path scaleToSize(ImageManager im, Dimension dim, String filename, boolean overwrite) throws IOException, ContentLibException {
        Path outputFile = Paths.get(filename);
        if (!overwrite && StorageProvider.getInstance().isFileExists(outputFile)) {
            return outputFile;
        }
        Path tempFile = Files.createTempFile("resize_tmp", ".tif"); // NOSONAR, using temp file is save
        try (OutputStream outputFileStream = Files.newOutputStream(tempFile)) {
            RenderedImage ri = im.scaleImageByPixel(dim, ImageManager.SCALE_TO_BOX, 0);
            try (JpegInterpreter pi = new JpegInterpreter(ri)) {
                pi.writeToStream(null, outputFileStream);
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
        switch (guiType) {
            case "both":
                return PluginGuiType.PART_AND_FULL;
            case "part":
                return PluginGuiType.PART;
            default:
                return PluginGuiType.FULL;
        }
    }

    @Override
    public String getPagePath() {
        return "/uii/ImageQAPlugin.xhtml";
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
        return getTitle();
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
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
        if ("".equals(ocrText)) {
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
            String currentImageURL = context.getExternalContext().getRequestContextPath() + ConfigurationHelper.getTempImagesPath() + session.getId()
                    + "_" + image.getImageName() + "_large_" + ".jpg";
            return currentImageURL.replace('\\', '/');
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
        return ConfigurationHelper.getTempImagesPathAsCompleteDirectory() + session.getId() + "_" + image.getImageName() + "_large" + ".jpg";
    }

    public String cmdMoveFirst() {
        if (pagesRTL) {
            if (this.pageNo != getLastPageNumber()) {
                this.pageNo = getLastPageNumber();
                getPaginatorList();
            }
        } else if (this.pageNo != 0) {
            this.pageNo = 0;
            getPaginatorList();
        }
        displayMode = "";
        return "";
    }

    public String cmdMovePrevious() {
        if (pagesRTL) {
            if (!isLastPage()) {
                this.pageNo++;
                getPaginatorList();
            }
        } else if (!isFirstPage()) {
            this.pageNo--;
            getPaginatorList();
        }
        displayMode = "";
        return "";
    }

    public String cmdMoveNext() {
        if (pagesRTL) {
            if (!isFirstPage()) {
                this.pageNo--;
                getPaginatorList();
            }
        } else if (!isLastPage()) {
            this.pageNo++;
            getPaginatorList();
        }
        displayMode = "";
        return "";
    }

    public String cmdMoveLast() {
        if (pagesRTL) {
            if (this.pageNo != 0) {
                this.pageNo = 0;
                getPaginatorList();
            }
        } else if (this.pageNo != getLastPageNumber()) {
            this.pageNo = getLastPageNumber();
            getPaginatorList();
        }
        displayMode = "";
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
            // do nothing
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
            // do nothing
        }
    }

    public String getImageMoveTo() {
        return this.imageIndex + 1 + "";
    }

    public int getLastPageNumber() {
        if ("part".equals(displayMode)) {
            return getLastPageNumberPartGUI();
        } else {
            int ret = this.allImages.size() / numberOfImagesInFullGUI;
            if (this.allImages.size() % numberOfImagesInFullGUI == 0) {
                ret--;
            }
            return ret;
        }
    }

    public int getLastPageNumberPartGUI() {
        int ret = this.allImages.size() / numberOfImagesInPartGUI;
        if (this.allImages.size() % numberOfImagesInPartGUI == 0) {
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

    public Long getPageNumberCurrent() {
        return Long.valueOf(this.pageNo + 1l);
    }

    public Long getPageNumberLast() {
        return Long.valueOf(getLastPageNumber() + 1l);
    }

    public Long getPageNumberLastPartGUI() {
        return Long.valueOf(getLastPageNumberPartGUI() + 1l);
    }

    public int getSizeOfImageList() {
        return allImages.size();
    }

    public int getThumbnailSize() {
        return THUMBNAIL_SIZE_IN_PIXEL;
    }

    public void setThumbnailSize(int value) {
        //
    }

    public String renameImages(SelectableImage myImage) {
        log.debug("Dateien werden jetzt umbenannt auf der Basis von: " + myImage.getNameParts());

        // rename SelectableImage objects, prepare renamingMap
        Map<Path, Path> renamingMap = prepareRenamingMap(myImage);

        // rename image files on the disk
        renameImageFilesOnDisk(renamingMap);

        if (!renamingMap.isEmpty()) {
            Helper.setFehlerMeldung("Error renaming files - file " + renamingMap.keySet().iterator().next() + " could not be renamed to "
                    + renamingMap.get(renamingMap.keySet().iterator().next()));
        }

        allImages = new ArrayList<>();

        initialize(this.step, returnPath);

        setImageIndex(imageIndex);
        return "";
    }

    private Map<Path, Path> prepareRenamingMap(SelectableImage myImage) {
        List<SelectableImage> selectedImages = allImages.stream().filter(SelectableImage::isSelected).collect(Collectors.toList());
        boolean renameSelectedOnly = !selectedImages.isEmpty();

        return renameSelectedOnly ? prepareRenamingMapForSelected(selectedImages, myImage) : prepareRenamingMapNoneSelected(myImage);
    }

    private Map<Path, Path> prepareRenamingMapNoneSelected(SelectableImage myImage) {
        log.debug("preparing renamingMap where none is selected");
        Map<Path, Path> renamingMap = new LinkedHashMap<>();

        int index = allImages.indexOf(myImage);
        int pageCounter = 0;
        String newCombinedName = myImage.getCombinedName();
        String oldCombinedName = myImage.getCombinedNameFromFilename();

        Iterator<SelectableImage> iterator = allImages.listIterator();
        while (iterator.hasNext()) {
            SelectableImage currentImage = iterator.next();
            String currentCombinedName = currentImage.getCombinedName();

            // only update names of images starting from the clicked one
            boolean indexCondition = allImages.indexOf(currentImage) >= index;
            // current combined name should be either equal to the new one or to the old one
            // for example, given the following list of combined names
            // A A A B B B C C C
            // if we want to set all A to B, then all items containing B will also be updated, since they contain the new combined name
            // if we want to set all A to D, then only the items containing A will be updated, since the other items contain neither the old nor the new combined name
            // if we want to set all A to C, then all items that contain previously C will also be updated
            boolean combinedNamesCondition = indexCondition && checkCombinedNames(currentCombinedName, newCombinedName, oldCombinedName);
            // when current image is not named yet
            boolean unnamedCondition = indexCondition && currentImage.isNotYetNamed();
            // rename current image if either condition applies
            boolean renameCurrentImage = combinedNamesCondition || unnamedCondition;
            if (renameCurrentImage) {
                currentImage.setNameParts(myImage.getNameParts());
                // add currentImage to renamingMap
                addImageToRenamingMap(renamingMap, currentImage, newCombinedName, ++pageCounter);
            } else if (newCombinedName.equals(currentCombinedName)) {
                ++pageCounter;
                // update names of images that meet the pattern and come after the clicked one
                if (indexCondition) {
                    addImageToRenamingMap(renamingMap, currentImage, newCombinedName, pageCounter);
                }
            }
        }

        return renamingMap;
    }

    private boolean checkCombinedNames(String currentCombinedName, String newCombinedName, String oldCombinedName) {
        return oldCombinedName.equals(currentCombinedName) || newCombinedName.equals(currentCombinedName);
    }

    private void addImageToRenamingMap(Map<Path, Path> renamingMap, SelectableImage currentImage, String newCombinedName, int pageCounter) {
        String suffix = currentImage.getImageName().substring(currentImage.getImageName().lastIndexOf("."));
        String currentPageCounter = PAGENUMBERFORMAT.format(pageCounter);
        currentImage.setPageCounterLabel(currentPageCounter);
        String newFilename = currentImage.getNamePrefix() + "_" + newCombinedName + currentPageCounter + suffix;
        Path imageFile = Paths.get(imageFolderName, currentImage.getImageName());
        Path newImageFile = Paths.get(imageFolderName, newFilename);
        renamingMap.put(imageFile, newImageFile);
        currentImage.setImageName(newFilename);
    }

    private Map<Path, Path> prepareRenamingMapForSelected(List<SelectableImage> selectedImages, SelectableImage myImage) {
        log.debug("preparing the renamingMap for selected images");
        int pageCounter = 0;
        String newCombinedName = myImage.getCombinedName();
        Map<Path, Path> renamingMap = new LinkedHashMap<>();
        Iterator<SelectableImage> selectedIterator = selectedImages.listIterator();
        Iterator<SelectableImage> allImagesIterator = allImages.listIterator();

        while (selectedIterator.hasNext()) {
            SelectableImage selectedImage = selectedIterator.next();
            while (allImagesIterator.hasNext()) {
                SelectableImage currentImage = allImagesIterator.next();
                if (currentImage == selectedImage) {
                    // add selectedImage to renamingMap
                    addImageToRenamingMap(renamingMap, selectedImage, newCombinedName, ++pageCounter);
                    // break out to continue with the next selected image
                    break;
                }

                // currentImage is not selectedImage, check if it meets the pattern, and if so update its name
                String currentCombinedName = currentImage.getCombinedName();
                if (currentCombinedName.equals(newCombinedName)) {
                    addImageToRenamingMap(renamingMap, currentImage, newCombinedName, ++pageCounter);
                }
            }
        }

        // update names of all images that meet the pattern
        while (allImagesIterator.hasNext()) {
            SelectableImage currentImage = allImagesIterator.next();
            String currentCombinedName = currentImage.getCombinedName();
            if (currentCombinedName.equals(newCombinedName)) {
                addImageToRenamingMap(renamingMap, currentImage, newCombinedName, ++pageCounter);
            }
        }

        return renamingMap;
    }

    private void renameImageFilesOnDisk(Map<Path, Path> renamingMap) {
        boolean renamed = true;
        while (renamed && !renamingMap.isEmpty()) {
            List<Path> toBeRenamedList = new ArrayList<>(renamingMap.keySet());
            Collections.reverse(toBeRenamedList);
            renamed = false;
            for (Path currentFile : toBeRenamedList) {
                Path newFile = renamingMap.get(currentFile);
                if (StorageProvider.getInstance().isFileExists(newFile) && !newFile.equals(currentFile)) {
                    // logger.error("Trying to rename " + currentFile.getName() + " to " +
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
    }

    public void deleteImage(Image myimage) {
        if (deletionCommand == null || deletionCommand.isEmpty()) {
            deleteImageViaJava(myimage);
        } else {
            callScript(myimage, deletionCommand, true);
        }
    }

    public void flipHorizontal(Image myimage) {
        callScript(myimage, flippingCommandHorizontal, false);
    }

    public void flipVertical(Image myimage) {
        callScript(myimage, flippingCommandVertical, false);
    }

    public void rotateRight(Image myimage) {
        callScript(myimage, rotationCommandRight, false);
    }

    public void rotateLeft(Image myimage) {
        callScript(myimage, rotationCommandLeft, false);
    }

    public void deleteSelection() {
        for (SelectableImage nextImage : allImages) {
            if (nextImage.isSelected()) {
                if (deletionCommand == null || deletionCommand.isEmpty()) {
                    deleteImageViaJava(nextImage);
                } else {
                    callScript(nextImage, deletionCommand, true);
                }
            }
        }
    }

    public void flipSelectionHorizontal() {
        for (SelectableImage im : allImages) {
            if (im.isSelected()) {
                callScript(im, flippingCommandHorizontal, false);
            }
        }
    }

    public void flipSelectionVertical() {
        for (SelectableImage im : allImages) {
            if (im.isSelected()) {
                callScript(im, flippingCommandVertical, false);
            }
        }
    }

    public void rotateSelectionRight() {
        for (SelectableImage im : allImages) {
            if (im.isSelected()) {
                callScript(im, rotationCommandRight, false);
            }
        }
    }

    public void rotateSelectionLeft() {
        for (SelectableImage im : allImages) {
            if (im.isSelected()) {
                callScript(im, rotationCommandLeft, false);
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

    public void callScript(Image myimage, List<String> commandParameter, boolean selectOtherImage) {
        int myindex = getImageIndex();
        if (selectOtherImage && myindex == allImages.indexOf(myimage)) {
            myindex--;
        }
        ConfigurationHelper configuration = ConfigurationHelper.getInstance();
        String imageURI = imageFolderName + myimage.getImageName();
        String imageFolderURI = imageFolderName;
        if (configuration.useS3()) {
            imageURI = S3FileUtils.string2Key(imageURI);
            imageFolderURI = S3FileUtils.string2Prefix(imageFolderURI);
        }

        List<String> commandLine = new ArrayList<>();
        for (String parameter : commandParameter) {
            commandLine.add(parameter.replace("IMAGE_FILE", imageURI).replace("IMAGE_FOLDER", imageFolderURI));
        }
        log.debug(commandLine);
        Process process = null;
        try {
            String[] callSequence = commandLine.toArray(new String[commandLine.size()]);
            ProcessBuilder pb = new ProcessBuilder(callSequence);
            process = pb.start();

            int result = process.waitFor();
            if (result != 0) {
                log.error("A problem occured while calling command '" + commandLine + "'. The error code was " + result);
                Helper.setFehlerMeldung("A problem occured while calling command '" + commandLine + "'. The error code was " + result);
            }
        } catch (IOException e) {
            log.error("IOException in rotate()", e);
            Helper.setFehlerMeldung("Aborted Command '" + commandLine + "' in callScript()!");
        } catch (InterruptedException e) {
            log.error("InterruptedException in callScript()", e);
            Helper.setFehlerMeldung("Command '" + commandLine + "' is interrupted in callScript()!");
        }

        allImages = new ArrayList<>();
        initialize(this.step, returnPath);

        setImageIndex(myindex);
    }

    public void selectAllImagesOnCurrentPage() {
        this.selectOrUnselectAllImagesOnCurrentPage(true);
    }

    public void unselectAllImagesOnCurrentPage() {
        this.selectOrUnselectAllImagesOnCurrentPage(false);
    }

    public void selectOrUnselectAllImagesOnCurrentPage(boolean value) {
        // Get the current number of images on a page
        int imagesOnPage;
        if ("part".equals(this.displayMode)) {
            imagesOnPage = this.numberOfImagesInPartGUI;
        } else {
            imagesOnPage = this.numberOfImagesInFullGUI;
        }

        // Get the first and the last index of the images in the current page
        int firstIndex = this.pageNo * imagesOnPage;
        int lastIndex = firstIndex + imagesOnPage - 1;

        // When it is the last page, the last indexable image might be some indices earlier
        int lastReachableIndex = allImages.size() - 1;
        if (lastReachableIndex < lastIndex) {
            lastIndex = lastReachableIndex;
        }

        // Change all images on the current page
        for (int index = firstIndex; index <= lastIndex; index++) {
            allImages.get(index).setSelected(value);
        }
    }

    public void selectAllImages() {
        for (SelectableImage im : allImages) {
            im.setSelected(true);
        }
    }

    public void unselectAllImages() {
        for (SelectableImage im : allImages) {
            im.setSelected(false);
        }
    }

    public void downloadSelectedImages() {
        try {
            FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
            ExternalContext ec = facesContext.getExternalContext();
            ec.responseReset();
            OutputStream responseOutputStream = ec.getResponseOutputStream();
            // zip file
            if ("zip".equals(downloadMode)) {
                ec.setResponseContentType("application/zip");
                ec.setResponseHeader("Content-Disposition", "attachment; filename=" + step.getProzess().getTitel() + ".zip");
                ZipOutputStream out = new ZipOutputStream(responseOutputStream);
                for (SelectableImage im : allImages) {
                    if (im.isSelected()) {
                        Path currentImagePath = Paths.get(imageFolderName, im.getImageName());
                        try (InputStream in = StorageProvider.getInstance().newInputStream(currentImagePath)) {
                            out.putNextEntry(new ZipEntry(im.getImageName()));
                            byte[] b = new byte[1024];
                            int count;
                            while ((count = in.read(b)) > 0) {
                                out.write(b, 0, count);
                            }
                        }
                    }
                }
                out.flush();
                out.close();
            } else if ("tar".equals(downloadMode)) {
                // tar file
                ec.setResponseContentType("application/x-tar");
                ec.setResponseHeader("Content-Disposition", "attachment; filename=" + step.getProzess().getTitel() + ".tar");

                try (BufferedOutputStream bof = new BufferedOutputStream(responseOutputStream);
                        TarArchiveOutputStream tar = new TarArchiveOutputStream(bof)) {
                    for (SelectableImage im : allImages) {
                        if (im.isSelected()) {
                            Path file = Paths.get(imageFolderName, im.getImageName());
                            TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), file.getFileName().toString());
                            tar.putArchiveEntry(tarEntry);
                            Files.copy(file, tar);
                            tar.closeArchiveEntry();
                        }
                    }

                    tar.finish();
                } catch (IOException e) {
                    log.error(e);
                }

            }

            else if ("tgz".equals(downloadMode)) {
                // tar file
                ec.setResponseContentType("application/gzip");
                ec.setResponseHeader("Content-Disposition", "attachment; filename=" + step.getProzess().getTitel() + ".tgz");

                try (
                        BufferedOutputStream bof = new BufferedOutputStream(responseOutputStream);
                        GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bof);
                        TarArchiveOutputStream tar = new TarArchiveOutputStream(gzos)) {
                    for (SelectableImage im : allImages) {
                        if (im.isSelected()) {
                            Path file = Paths.get(imageFolderName, im.getImageName());
                            TarArchiveEntry tarEntry = new TarArchiveEntry(file.toFile(), file.getFileName().toString());
                            tar.putArchiveEntry(tarEntry);
                            Files.copy(file, tar);
                            tar.closeArchiveEntry();
                        }
                    }

                    tar.finish();
                } catch (IOException e) {
                    log.error(e);
                }

            }

            facesContext.responseComplete();
        } catch (IOException e) {
            log.error(e);
        }
    }

    public void downloadSelectedImagesAsPdf() throws IOException {

        Path imagesPath = Paths.get(imageFolderName);
        // put all selected images into a URL
        String imagesParameter =
                allImages.stream().filter(SelectableImage::isSelected).map(SelectableImage::getImageName).collect(Collectors.joining("$"));

        URI goobiContentServerUrl = UriBuilder.fromUri(new HelperForm().getServletPathWithHostAsUrl())
                .path("api")
                .path("process")
                .path("image")
                .path(Integer.toString(step.getProzess().getId()))
                .path("media") //dummy, replaced by images query param
                .path("00000001.tif") //dummy, replaced by images query param
                .path(step.getProzess().getTitel() + ".pdf")
                .queryParam("imageSource", imagesPath.toUri())
                .queryParam("images", imagesParameter)
                .build();

        FacesContext context = FacesContextHelper.getCurrentFacesContext();
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

    @SuppressWarnings("unused")
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

    //this is set whenever setImage() is called.
    @Getter
    private boolean showImageComments = false;

    // =========================== Use ImageCommentPropertyHelper To Save Comments =========================== //

    private ImageCommentPropertyHelper commentPropertyHelper;

    private ImageCommentPropertyHelper getCommentPropertyHelper() {
        if (commentPropertyHelper == null) {
            // This method is somehow already called when we start Goobi, by which time step is still uninitialized. Therefore the following if block is needed to ensure a successful start.
            // But null as returned value will never be used by the methods getCommentPropertyForImage and setCommentPropertyForImage, since they are both triggered within a step.
            if (step == null) {
                log.debug("step is not initialized yet");
                return null;
            }

            commentPropertyHelper = new ImageCommentPropertyHelper(step.getProzess());
        }
        return commentPropertyHelper;
    }

    public String getCommentPropertyForImage() {
        if (getImage() == null) {
            return null;
        }

        String onlyImageFolder = Paths.get(imageFolderName).getFileName().toString();
        Optional<ImageComment> comment = getCommentPropertyHelper().getComment(onlyImageFolder, getImage().getImageName());
        if (!comment.isPresent()) {
            return null;
        }
        return comment.get().getComment();
    }

    public void setCommentPropertyForImage(String comment) {

        if (getImage() == null) {
            return;
        }

        //only save new log entry if the comment has changed
        String oldComment = getCommentPropertyForImage();
        if (comment == null || (oldComment != null && comment.contentEquals(oldComment)) || (oldComment == null && comment.isEmpty())) {
            return;
        }

        String onlyImageFolder = Paths.get(imageFolderName).getFileName().toString();
        ImageComment newComment = new ImageComment(
                comment,
                getImage().getImageName(),
                onlyImageFolder,
                new Date(),
                Helper.getCurrentUser().getLogin(),
                step.getNormalizedTitle(),
                ImageComment.ImageCommentLocation.PLUGIN_IMAGEQA);
        getCommentPropertyHelper().setComment(imageFolderName, getImage().getImageName(), newComment);
    }

    // ========================== // Use ImageCommentPropertyHelper To Save Comments ========================== //

    // ============================== Handle Persistent Rotation Above Big Image ============================== //
    public void rotateCurrentImageToLeft() {
        rotateLeft(getImage());
    }

    public void rotateCurrentImageToRight() {
        rotateRight(getImage());
    }

    public boolean isAllowPersistentRotation() {
        return allowRotation && persistentRotationAboveBigImage;
    }

    // ============================= // Handle Persistent Rotation Above Big Image ============================= //

}

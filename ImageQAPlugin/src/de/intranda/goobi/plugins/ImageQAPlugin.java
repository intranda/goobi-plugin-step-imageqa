package de.intranda.goobi.plugins;

import java.awt.Dimension;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

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
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentLibImageException;
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
    private int IMAGE_SIZE_IN_PIXEL = 800;

    private int pageNo = 0;

    private int imageIndex = 0;

    private String imageFolderName = "";

    private List<Image> allImages = new ArrayList<Image>();

    private Image image = null;

    @Override
    public void initialize(Step step, String returnPath) {

        NUMBER_OF_IMAGES_PER_PAGE = ConfigPlugins.getPluginConfig(this).getInt("numberOfImagesPerPage", 50);
        THUMBNAIL_SIZE_IN_PIXEL = ConfigPlugins.getPluginConfig(this).getInt("thumbnailsize", 200);
        IMAGE_SIZE_IN_PIXEL = ConfigPlugins.getPluginConfig(this).getInt("imagesize", 800);
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
                List<String> imageNameList = Arrays.asList(imageNameArray);
                Collections.sort(imageNameList);
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

        url.append("/cs").append("?action=").append("image").append("&format=").append("png").append("&sourcepath=").append("file://" + imageFolderName + currentImage.getImageName())
        .append("&width=").append(THUMBNAIL_SIZE_IN_PIXEL).append("&height=").append(THUMBNAIL_SIZE_IN_PIXEL);
        
        currentImage.setThumbnailUrl(url.toString());

    }

    private void scaleFile(String inFileName, String outFileName, int size) throws IOException, ContentLibImageException {

        ImageManager im = new ImageManager(new File(inFileName).toURI().toURL());
        Dimension dim = new Dimension();
        dim.setSize(size, size);
        RenderedImage ri = im.scaleImageByPixel(dim, ImageManager.SCALE_TO_BOX, 0);
        JpegInterpreter pi = new JpegInterpreter(ri);
        FileOutputStream outputFileStream = new FileOutputStream(outFileName);
        pi.writeToStream(null, outputFileStream);
        outputFileStream.close();

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
            String currentImageURL = ConfigurationHelper.getTempImagesPath() + session.getId() + "_" + image.getImageName() + "_large_" + ".png";
            return currentImageURL;
        }
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
        FacesContext context = FacesContextHelper.getCurrentFacesContext();
        HttpSession session = (HttpSession) context.getExternalContext().getSession(false);
        String currentImageURL =
                ConfigurationHelper.getTempImagesPathAsCompleteDirectory() + session.getId() + "_" + image.getImageName() + "_large_" + ".png";
        try {
            if (currentImageURL != null) {
                scaleFile(imageFolderName + image.getImageName(), currentImageURL, IMAGE_SIZE_IN_PIXEL);
            }
        } catch (ContentLibImageException | IOException e) {
            logger.error(e);
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

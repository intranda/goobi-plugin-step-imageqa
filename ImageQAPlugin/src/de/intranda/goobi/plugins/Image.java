package de.intranda.goobi.plugins;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

public class Image {

    private String imageName;
    private int order;
    private String thumbnailUrl;
    private List<ImageLevel> imageLevels = new ArrayList<ImageLevel>();
    private String tooltip;
    private Dimension size = null;
    

    public Image(String imageName, int order, String thumbnailUrl, String tooltip) {
        this.imageName = imageName;
        this.order = order;
        this.thumbnailUrl = thumbnailUrl;
        this.tooltip = tooltip;
    }

    public String getImageName() {
        return imageName;
    }
    
    public String getImageNameShort() {
        if (imageName.length()>25){
        	return "..." + imageName.substring(imageName.length()-25, imageName.length());
        }else{
        	return imageName;
        }
    }

    public int getOrder() {
        return order;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getTooltip() {
        return tooltip;
    }
    
    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public void addImageLevel(String imageUrl, int size) {
        int width, height;
        double scale = size/(double)(Math.max(getSize().height, getSize().width));
        Dimension dim = new Dimension((int)(getSize().width*scale), (int)(getSize().height*scale));
        ImageLevel layer = new ImageLevel(imageUrl, dim);
        imageLevels.add(layer);
    }

    public List<ImageLevel> getImageLevels() {
        return this.imageLevels;
    }

    public Dimension getSize() {
        return size;
    }

    public void setSize(Dimension size) {
        this.size = size;
    }
    
    

}

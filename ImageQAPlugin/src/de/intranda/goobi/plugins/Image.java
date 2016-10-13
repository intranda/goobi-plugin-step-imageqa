package de.intranda.goobi.plugins;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Image {

    private String imageName;
    private int order;
    private String thumbnailUrl;
    private String largeThumbnailUrl;
    private List<ImageLevel> imageLevels = new ArrayList<ImageLevel>();
    private String tooltip;
    private Dimension size = null;
    private String tempName;
    private boolean selected = false;
    

    public Image(String imageName, int order, String thumbnailUrl, String tooltip) {
        this.imageName = imageName;
        this.order = order;
        this.thumbnailUrl = thumbnailUrl;
        this.tooltip = tooltip;
    }

    
    public String getImageNameShort() {
        if (imageName.length()>25){
        	return "..." + imageName.substring(imageName.length()-25, imageName.length());
        }else{
        	return imageName;
        }
    }

    public void addImageLevel(String imageUrl, int size) {
        double scale = size/(double)(Math.max(getSize().height, getSize().width));
        Dimension dim = new Dimension((int)(getSize().width*scale), (int)(getSize().height*scale));
        ImageLevel layer = new ImageLevel(imageUrl, dim);
        imageLevels.add(layer);
    }
}

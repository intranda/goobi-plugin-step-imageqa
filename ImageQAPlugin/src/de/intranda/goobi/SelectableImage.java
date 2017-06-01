package de.intranda.goobi;

import lombok.Data;

public class SelectableImage extends Image {
    
    private boolean selected;
    private String tempName;
    
    public SelectableImage(String imageName, int order, String thumbnailUrl, String largeThumbnailUrl, String tooltip) {
        super(imageName, order, thumbnailUrl, largeThumbnailUrl, tooltip);
        this.selected = false;
    }
    
    public String getImageNameShort() {
        if (getImageName().length()>25){
            return "..." + getImageName().substring(getImageName().length()-25, getImageName().length());
        }else{
            return getImageName();
        }
    }

    /**
     * @return the selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * @param selected the selected to set
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * @return the tempName
     */
    public String getTempName() {
        return tempName;
    }

    /**
     * @param tempName the tempName to set
     */
    public void setTempName(String tempName) {
        this.tempName = tempName;
    }
    
    
    
}

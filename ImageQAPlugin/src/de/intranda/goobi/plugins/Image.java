package de.intranda.goobi.plugins;

public class Image {

    private String imageName;
    private int order;
    private String thumbnailUrl;
    private String tooltip;
    

    public Image(String imageName, int order, String thumbnailUrl, String tooltip) {
        this.imageName = imageName;
        this.order = order;
        this.thumbnailUrl = thumbnailUrl;
        this.tooltip = tooltip;
    }

    public String getImageName() {
        return imageName;
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
}

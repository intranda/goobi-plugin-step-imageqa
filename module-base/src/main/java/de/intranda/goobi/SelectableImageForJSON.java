package de.intranda.goobi;

import lombok.Data;

@Data
public class SelectableImageForJSON {
    private String url;
    private String imageName;

    public SelectableImageForJSON(SelectableImage image) {
        this.url = image.getUrl();
        this.imageName = image.getImageName();
    }
}

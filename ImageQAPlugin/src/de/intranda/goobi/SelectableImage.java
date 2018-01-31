package de.intranda.goobi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import de.sub.goobi.metadaten.Image;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelectableImage extends Image {
    
    private static final String NAMEPART_SEPARATOR = "_";
    
    private boolean selected;
    private String namePrefix;
    private List<NamePart> nameParts = new ArrayList<>();
    private String pageCounterLabel;

    
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
     * @return The concatenation of all namePart values, each followed by the namepart-separator-char (e.g. "a_b_c_")
     */
    public String getCombinedName() {
        StringBuilder sb = new StringBuilder("");
        for (NamePart namePart : nameParts) {
            sb.append(namePart.getValue()).append(NAMEPART_SEPARATOR);
        }
        return sb.toString();
    }

    public void initNameParts(List<NamePart> defaultNameParts) {
        this.nameParts = new ArrayList<>();
        String imageBaseName = FilenameUtils.getBaseName(getImageName());
        String[] imageNameParts = imageBaseName.split(NAMEPART_SEPARATOR);
        if(imageNameParts.length > 0) {
            namePrefix = imageNameParts[0];
        }
        if(imageNameParts.length > 1) {
            pageCounterLabel = imageNameParts[imageNameParts.length-1];
        }
        
        if(imageNameParts.length > 1) {            
            imageNameParts = Arrays.copyOfRange(imageNameParts, 1, imageNameParts.length-1);
        } else {
            imageNameParts = new String[0];
        }
        for (int i = 0; i < defaultNameParts.size(); i++) {
            NamePart newNamePart = new NamePart(defaultNameParts.get(i));
            if(i < imageNameParts.length) {
                String origNamePart = imageNameParts[i];
                newNamePart.setValue(origNamePart);
            }
            nameParts.add(newNamePart);
        }
        
    }

    /**
     * @return true if the actual file name does not contain the combined name of the nameParts
     */
    public boolean isNotYetNamed() {
        return !getImageName().contains(getCombinedName());
    }

    public String getCombinedNameFromFilename() {
        String imageBaseName = FilenameUtils.getBaseName(getImageName());
        String[] imageNameParts = imageBaseName.split(NAMEPART_SEPARATOR);
        if(imageNameParts.length > 1) {            
            imageNameParts = Arrays.copyOfRange(imageNameParts, 1, imageNameParts.length-1);
        } else {
            imageNameParts = new String[0];
        }
        StringBuilder sb = new StringBuilder("");
        for (String namePart : imageNameParts) {
            sb.append(namePart).append(NAMEPART_SEPARATOR);
        }
        return sb.toString(); 
    }

    /**
     * Set name parts to copy of given name parts
     * 
     * @param newNameParts
     */
    public void setNameParts(List<NamePart> newNameParts) {
        this.nameParts = new ArrayList<>();
        for (NamePart namePart : newNameParts) {
            this.nameParts.add(new NamePart(namePart));
        }
    }

    
    
    
}

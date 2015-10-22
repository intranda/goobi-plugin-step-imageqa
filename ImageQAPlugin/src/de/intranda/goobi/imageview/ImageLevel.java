/*************************************************************************
 * 
 * Copyright intranda GmbH
 * 
 * ************************* CONFIDENTIAL ********************************
 * 
 * [2003] - [2015] intranda GmbH, Bertha-von-Suttner-Str. 9, 37085 Göttingen, Germany 
 * 
 * All Rights Reserved.
 * 
 * NOTICE: All information contained herein is protected by copyright. 
 * The source code contained herein is proprietary of intranda GmbH. 
 * The dissemination, reproduction, distribution or modification of 
 * this source code, without prior written permission from intranda GmbH, 
 * is expressly forbidden and a violation of international copyright law.
 * 
 *************************************************************************/
package de.intranda.goobi.imageview;

import java.awt.Dimension;

/**
 * @author florian
 *
 */
public class ImageLevel implements Comparable<ImageLevel>{
    
    private String url;
    private Dimension size;
    private int rotation;
    
    public ImageLevel(String url, Dimension size) {
        super();
        this.url = url;
        this.size = size;
        this.rotation = 0;
    }
    
    public ImageLevel(String url, int width, int height) {
        super();
        this.url = url;
        this.size = new Dimension(width, height);
        this.rotation = 0;
    }

    public ImageLevel(String url, Dimension size, int currentRotate) {
        super();
        this.url = url;
        this.size = size;
        this.rotation = currentRotate;
    }

    public String getUrl() {
        return url;
    }

    public Dimension getSize() {
        if(rotation % 180 == 0) {            
            return size;
        } else {
            return new Dimension(size.height, size.width);
        }
    }
    
    public int getWidth() {
        return rotation%180 == 90 ? size.height : size.width;
    }
    
    public int getHeight() {
        return rotation%180 == 90 ? size.width : size.height;
    }

    @Override
    public String toString() {
        return "[\"" + url + "\"," + getWidth() + "," + getHeight() + "]";
    }

    @Override
    public int compareTo(ImageLevel other) {
        return Integer.compare(size.width, other.size.width);
    }
    
}

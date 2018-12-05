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
var viewImage = (function (osViewer) {
    'use strict';

    var _debug = false;

    osViewer.controls = {
        myZoomTo: function (zoomTo) {
            if (_debug) {
                console.log('osViewer.controls.myZoomTo: zoomTo - ' + zoomTo);
            }

            var zoomBy = parseFloat(zoomTo) / osViewer.viewer.viewport.getZoom();

            if (_debug) {
                console.log('osViewer.controls.myZoomTo: zoomBy - ' + zoomBy);
            }

            osViewer.viewer.viewport.zoomBy(zoomBy, osViewer.viewer.viewport.getCenter(false), true);
        },
        setFullScreen: function (enable) {
            if (_debug) {
                console.log('osViewer.controls.setFullScreen: enable - ' + enable);
            }

            osViewer.viewer.setFullScreen(enable);
        },
        goHome: function (resetRotation) {
            if (_debug) {
                console.log('osViewer.controls.goHome: bool - ' + bool);
            }

            osViewer.viewer.viewport.goHome(true);
            osViewer.viewer.viewport.zoomTo(osViewer.viewer.viewport.getMinZoom(), null, true);
            if(resetRotation) {                
                osViewer.controls.rotateTo(0);
            }
        },
        zoomIn: function () {
            if (_debug) {
                console.log('osViewer.controls.zoomIn: zoomSpeed - ' + osViewer.defaults.global.zoomSpeed);
            }

            osViewer.viewer.viewport.zoomBy(osViewer.defaults.global.zoomSpeed, osViewer.viewer.viewport.getCenter(false), false);
        },
        zoomOut: function () {
            if (_debug) {
                console.log('osViewer.controls.zoomOut: zoomSpeed - ' + osViewer.defaults.global.zoomSpeed);
            }

            osViewer.viewer.viewport.zoomBy(1 / osViewer.defaults.global.zoomSpeed, osViewer.viewer.viewport.getCenter(false), false);
        },
        rotateRight: function () {
            if (_debug) {
                console.log('osViewer.controls.rotateRight');
            }

            var newRotation = osViewer.viewer.viewport.getRotation() + 90;
            osViewer.controls.rotateTo(newRotation);
        },
        rotateLeft: function () {
            if (_debug) {
                console.log('osViewer.controls.rotateLeft');
            }

            var newRotation = osViewer.viewer.viewport.getRotation() - 90;
            osViewer.controls.rotateTo(newRotation);
        },
        rotateTo: function (newRotation) {
            if (_debug) {
                console.log('osViewer.controls.rotateTo: newRotation - ' + newRotation);
            }
            
            var zoomedOut = false;
            var zoomDiffToHomeZoom = osViewer.viewer.viewport.getZoom() - osViewer.viewer.viewport.getHomeZoom();
            var zoomDiffToMinZoom = osViewer.viewer.viewport.getZoom() - osViewer.viewer.viewport.getMinZoom();
            if(Math.abs(zoomDiffToMinZoom) < 0.000000001 || Math.abs(zoomDiffToHomeZoom) < 0.000000001 || zoomDiffToHomeZoom < 0) {
                osViewer.viewer.viewport.zoomTo(osViewer.viewer.viewport.getMinZoom(), null, true);
                zoomedOut = true;
            }

            
            osViewer.viewer.viewport.setRotation(newRotation);
            
            if (newRotation % 180 !== 0 ) {
                var imageBounds = osViewer.viewer.viewport.imageToViewportCoordinates(osViewer.viewer.viewport.contentSize);
                var minZoom = imageBounds.x / imageBounds.y;
                //console.log("minZoom = " + imageBounds.x + "/" + imageBounds.y + " = " + minZoom);
                if (minZoom < 1) {
                    osViewer.viewer.viewport.minZoomLevel = minZoom*osViewer.defaults.global.minZoomLevel;
                    if(zoomedOut) {                        
                        osViewer.viewer.viewport.zoomTo(minZoom*osViewer.defaults.global.minZoomLevel, null, true);
                    }
                } else {
                    osViewer.viewer.viewport.minZoomLevel = 1/minZoom*osViewer.defaults.global.minZoomLevel;
                    if(zoomedOut) {                        
                        osViewer.viewer.viewport.zoomTo(1/minZoom*osViewer.defaults.global.minZoomLevel, null, true);
                    }
                }
            } else {
                osViewer.viewer.viewport.minZoomLevel = osViewer.defaults.global.minZoomLevel;
                if(zoomedOut) {                    
                    osViewer.viewer.viewport.fitHorizontally(true);
                }
            }

            if (osViewer.overlays) {
                var rects = osViewer.overlays.getRects();
                for (var i in rects) {
                    var rect = new OpenSeadragon.Rect(rects[i].rect.x,
                        rects[i].rect.y, rects[i].rect.width,
                        rects[i].rect.height);
                    if (newRotation === 90) {
                        rect.x += (rect.height - rect.width) / 2;
                        rect.y += (rect.height - rect.width) / 2;
                    } else if (newRotation === 270 || newRotation === -90) {
                        rect.x -= (rect.height - rect.width) / 2;
                        rect.y -= (rect.height - rect.width) / 2;
                    }
                    osViewer.viewer.updateOverlay(rects[i].rectElement, rect, 0);
                }
            }
        }
    };

    return osViewer;

})(viewImage || {}, jQuery);
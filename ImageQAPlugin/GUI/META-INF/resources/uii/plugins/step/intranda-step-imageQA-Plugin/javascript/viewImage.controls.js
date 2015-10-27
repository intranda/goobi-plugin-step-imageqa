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
        goHome: function (bool) {
            if (_debug) {
                console.log('osViewer.controls.goHome: bool - ' + bool);
            }

            osViewer.viewer.viewport.goHome(bool);
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

            osViewer.viewer.viewport.setRotation(newRotation);
            var zoomDiff = osViewer.viewer.viewport.getZoom() - osViewer.viewer.viewport.getHomeZoom();

            if (newRotation % 180 !== 0 && (Math.abs(zoomDiff) < 0.000000001 || zoomDiff < 0)) {
                var imageBounds = osViewer.viewer.viewport.imageToViewportRectangle(osViewer.fullImageBounds);
                var destZoom = imageBounds.width / imageBounds.height;
                if (destZoom < 1) {
                    osViewer.viewer.viewport.minZoomLevel = destZoom;
                    osViewer.viewer.viewport.zoomTo(destZoom, null, true);
                } else {
                    osViewer.viewer.viewport.fitHorizontally(true);
                }
            } else if (Math.abs(zoomDiff) < 0.000000001 || zoomDiff < 0) {
                osViewer.viewer.viewport.fitHorizontally(true);
                osViewer.viewer.viewport.minZoomLevel = 1;
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
/**
 * ImageQA Plugin JavaScript functionality
 * This file contains all JavaScript functions for the ImageQA plugin
 */

// Global variables
var viewImage;
var world;

// Initialize on window load
window.onload = function() {
    loadImages();
}

/**
 * Load and configure thumbnail images
 */
function loadThumbnails() {
    var height = parseInt(window.imageQAConfig?.thumbnailSize || 0);
    if (height) {
        $('.goobi-thumbnail-image').css('height', (height + 25) + 'px');
        $('.goobi-thumbnail-image .thumb').css('max-height', height + 'px');
        $('.goobi-thumbnail-image .thumb canvas').css('max-height', height + 'px');
        $('.goobi-thumbnail-image').css('max-width', (height) + 'px');
    }
}

/**
 * Handle AJAX events for JSF
 */
function setupAjaxEvents() {
    if (typeof faces !== 'undefined') {
        faces.ajax.addOnEvent(function(data) {
            var ajaxstatus = data.status; // Can be "begin", "complete" and "success"
            switch (ajaxstatus) {
                case "success": // This is called when ajax response is successfully processed.
                    loadImages();
                    loadThumbnails();
                    break;
            }
        });
    }
}

/**
 * Handle click events on go-to-image button
 */
const clickGoToImageButton = (event) => {
    syncValues(event.target);
    if(event.key === 'Enter' || event.keyCode === 13) {
        event.preventDefault();
        let btn;
        const element = event.target;
        if (element.id.endsWith('second_image:txtImageMoveTo2')) {
            btn = document.querySelector('[id$="first_image:cmdMoveTo"]');
        } else if (element.id.endsWith('first_image:txtImageMoveTo2')) {
            btn = document.querySelector('[id$="second_image:cmdMoveTo"]');
        } else if (element.id.endsWith('second:txtMoveTo2')) {
            btn = document.querySelector('[id$="first:cmdMoveTo"]');
        } else if (element.id.endsWith('first:txtMoveTo2')) {
            btn = document.querySelector('[id$="second:cmdMoveTo"]');
        }
        if (btn) {
            btn.click();
        }
    }
};

/**
 * Synchronize values between input fields
 */
const syncValues = (element) => {
    let otherInput;
    if (element.id.endsWith('second_image:txtImageMoveTo2')) {
        otherInput = document.querySelector('[id$="first_image:txtImageMoveTo2"]');
    } else if (element.id.endsWith('first_image:txtImageMoveTo2')) {
        otherInput = document.querySelector('[id$="second_image:txtImageMoveTo2"]');
    } else if (element.id.endsWith('second:txtMoveTo2')) {
        otherInput = document.querySelector('[id$="first:txtMoveTo2"]');
    } else if (element.id.endsWith('first:txtMoveTo2')) {
        otherInput = document.querySelector('[id$="second:txtMoveTo2"]');
    }
    if (otherInput) {
        otherInput.value = element.value;
    }
}

/**
 * Copy value between input fields with keyboard support
 */
function copyValue(element, e) {
    console.log(element.id);
    console.log("data attr ", element.dataset.input);
    if(element.dataset.input) {
        document.querySelectorAll("[data-input='"+element.dataset.input+"']").forEach(input => input.value = element.value );
    } else if (element.id =='qaform:first:txtMoveTo2') {
        document.getElementById('qaform:second:txtMoveTo2').value = element.value;
    } else if (element.id =='qaform:second:txtMoveTo2'){
        document.getElementById('qaform:first:txtMoveTo2').value = element.value;
    } else if (element.id =='qaform:first_image:txtImageMoveTo2') {
        document.getElementById('qaform:second_image:txtImageMoveTo2').value = element.value;
    } else {
        document.getElementById('qaform:first_image:txtImageMoveTo2').value = element.value;
    }

    const keycode = e.keyCode;

    if (keycode == 13) {
        const btn = document.querySelector('[id$="cmdImageMoveTo"]');
        if (btn) {
            btn.click();
        }
        return false;
    } else
        return true;
}

/**
 * Setup keyboard shortcuts
 */
function setupKeyboardShortcuts() {
    // Check if jQuery hotkeys is available and shortcut prefix is defined
    if (typeof $ !== 'undefined' && $.fn.bind && window.imageQAConfig?.shortcutPrefix) {
        $(document).bind('keyup', window.imageQAConfig.shortcutPrefix + '+right', function() {
            var myButton = document.getElementById("qaform:first_image:imageNext");
            if (myButton != null) {
                myButton.click();
            }
        });

        $(document).bind('keyup', window.imageQAConfig.shortcutPrefix + '+left', function() {
            var myButton = document.getElementById("qaform:first_image:imageBack");
            if (myButton != null) {
                myButton.click();
            }
        });
    }
}

/**
 * Launch fullscreen mode for an element
 */
function launchFullScreen(element) {
    if(element.requestFullScreen) {
        element.requestFullScreen();
    } else if(element.mozRequestFullScreen) {
        element.mozRequestFullScreen();
    } else if(element.webkitRequestFullScreen) {
        element.webkitRequestFullScreen();
    }
}

/**
 * Initialize image viewer with configuration
 */
function initializeImageViewer(config) {
    if (!config) {
        console.error('Image viewer configuration is required');
        return;
    }

    var mediaType = config.mediaType;

    if(mediaType == "image") {
        initializeImageView(config);
    } else if(mediaType == "object") {
        initializeObjectView(config);
    } else if(mediaType == "x3dom") {
        initializeX3DOMView(config);
    }
}

/**
 * Initialize image view
 */
function initializeImageView(config) {
    //init zoom persistence
    let imageZoomPersistenceId = $('#persistenceId').val();
    if(config.persistZoom && imageZoomPersistenceId && imageZoomPersistenceId.length > 0) {
        console.log("persist image zoom with id ", imageZoomPersistenceId);
        config.viewer.global.persistenceId = imageZoomPersistenceId;
        config.viewer.global.persistZoom = true;
        config.viewer.global.persistRotation = true;
    }

    viewImage = new ImageView.Image(config.viewer);
    viewImage.load()
    .then(function(image) {
        image.onFirstTileLoaded()
        .then(function() {
            $('#ajaxloader_image').fadeOut(800);
        })
        .catch(function() {
            $('#ajaxloader_image').fadeOut(800);
        })
    })
    .catch(function(error){
        console.error('Error opening image', error);
        $('#ajaxloader_image').fadeOut(800);
        $('#' + config.viewer.global.divId).html('Failed to load image: "' + error + '"');
    });
}

/**
 * Initialize 3D object view
 */
function initializeObjectView(config) {
    $('#ajaxloader_image').show();
    world = WorldGenerator.create(config.world);
    world.loadObject({
        url: config.objectUrl,
        position: { x:0, y:0, z:-0 },
        rotation:  { x:0, y:0, z:0 },
        size:  10,
        material: {
            color: 0x44bb33
        },
        focus: true,
        onTick: function(object, time) {
            if(object) {
                // object.rotation.set(0,  Math.PI/180 * time, 0);
            }
        }
    }).then(function(object) {
        $('#ajaxloader_image').fadeOut(2000);
        world.render();
    }).catch(function(error) {
        $('#ajaxloader_image').fadeOut(2000);
        console.error("failed to load: ", error);
    })
}

/**
 * Initialize X3DOM view
 */
function initializeX3DOMView(config) {
    $('#ajaxloader_image').show();
    new X3DLoader().load($('#mainImage'), config.objectUrl,
        function(){
            $('#ajaxloader_image').fadeOut(2000);
            // console.log("loaded")
        },
        function(){
            // console.log("progress");
        },
        function(error){
            $('#ajaxloader_image').fadeOut(2000);
            console.error("error", error);
        })
}

/**
 * Free JavaScript resources
 */
function freeJSResources(data) {
    if(!data || data.status == 'begin') {
        if(viewImage) {
            // console.log("closing OpenSeadragon viewer");
            viewImage.close();
        }
        if(world) {
            // console.log("disposing 3d scene");
            world.dispose();
        }
    }
}

// Initialize when DOM is ready
$(document).ready(function() {
    loadThumbnails();
    setupAjaxEvents();
    setupKeyboardShortcuts();
});

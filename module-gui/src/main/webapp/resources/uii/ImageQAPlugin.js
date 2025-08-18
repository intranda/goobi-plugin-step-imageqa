/**
 * ImageQA Plugin JavaScript functionality
 */

(function() {
    'use strict';

    // Global variables - check if already exists to prevent redeclaration
    if (typeof window.viewImage === 'undefined') {
        window.viewImage = null;
    }
    if (typeof window.world === 'undefined') {
        window.world = null;
    }

    // Cache frequently accessed DOM elements
    const domCache = {
        persistenceId: null,
        mainImage: null,
        thumbnailImages: null,
        imageButtons: {
            next: null,
            back: null
        }
    };

    // Debug mode flag
    const DEBUG_MODE = false;

    /**
     * Initialize DOM cache for frequently accessed elements
     */
    const initializeDOMCache = () => {
        // Use modern DOM methods instead of jQuery where possible
        domCache.persistenceId = document.getElementById('persistenceId');
        domCache.mainImage = document.getElementById('mainImage');
        domCache.imageButtons.next = document.getElementById("qaform:first_image:imageNext");
        domCache.imageButtons.back = document.getElementById("qaform:first_image:imageBack");
    };

    /**
     * Utility function for debug logging
     */
    const debugLog = (message, data = null) => {
        if (DEBUG_MODE) {
            if (data) {
                console.log(message, data);
            } else {
                console.log(message);
            }
        }
    };

    /**
     * Modern DOM ready function to replace $(document).ready()
     */
    const domReady = (callback) => {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', callback);
        } else {
            callback();
        }
    };

    // Initialize on window load
    window.addEventListener('load', () => {
        // Add a small delay to ensure DOM is fully rendered
        setTimeout(() => {
            loadThumbnails();
            setupLazyImageLoading();
        }, 100);
    });

    /**
     * Load and configure thumbnail images
     */
    const loadThumbnails = () => {
        const height = parseInt(window.imageQAConfig?.thumbnailSize || 0);

        if (height) {
            // Use modern DOM methods instead of jQuery
            const thumbnailImages = document.querySelectorAll('.goobi-thumbnail-image');
            const thumbs = document.querySelectorAll('.goobi-thumbnail-image .thumb');
            const canvases = document.querySelectorAll('.goobi-thumbnail-image .thumb canvas');

            // Apply all changes in a single batch to minimize reflow
            const heightPx = `${height + 25}px`;
            const maxHeightPx = `${height}px`;
            const maxWidthPx = `${height}px`;

            // Batch DOM updates using requestAnimationFrame for better performance
            requestAnimationFrame(() => {
                thumbnailImages.forEach(img => {
                    img.style.height = heightPx;
                    img.style.maxWidth = maxWidthPx;
                });

                thumbs.forEach(thumb => {
                    thumb.style.maxHeight = maxHeightPx;
                });

                canvases.forEach(canvas => {
                    canvas.style.maxHeight = maxHeightPx;
                });
            });

            // Cache for future use
            domCache.thumbnailImages = thumbnailImages;
        }
    };

    /**
     * Handle AJAX events for JSF
     */
    const setupAjaxEvents = () => {
        if (typeof faces !== 'undefined') {
            faces.ajax.addOnEvent(function(data) {
                const ajaxstatus = data.status; // Can be "begin", "complete" and "success"
                switch (ajaxstatus) {
                    case "success": // This is called when ajax response is successfully processed.
                        // Add a small delay to ensure DOM updates are complete
                        setTimeout(() => {
                            loadThumbnails();
                            setupLazyImageLoading();
                        }, 50);
                        break;
                }
            });
        }
    };

    /**
     * Handle click events on go-to-image button
     */
    const clickGoToImageButton = (event) => {
        syncValues(event.target);
        if(event.key === 'Enter' || event.keyCode === 13) {
            event.preventDefault();

            const element = event.target;
            const buttonMap = {
                'second_image:txtImageMoveTo2': '[id$="first_image:cmdMoveTo"]',
                'first_image:txtImageMoveTo2': '[id$="second_image:cmdMoveTo"]',
                'second:txtMoveTo2': '[id$="first:cmdMoveTo"]',
                'first:txtMoveTo2': '[id$="second:cmdMoveTo"]'
            };

            // Find matching pattern
            const pattern = Object.keys(buttonMap).find(key => element.id.endsWith(key));
            if (pattern) {
                const btn = document.querySelector(buttonMap[pattern]);
                if (btn) {
                    btn.click();
                }
            }
        }
    };

    /**
     * Synchronize values between input fields
     */
    const syncValues = (element) => {
        const inputMap = {
            'second_image:txtImageMoveTo2': '[id$="first_image:txtImageMoveTo2"]',
            'first_image:txtImageMoveTo2': '[id$="second_image:txtImageMoveTo2"]',
            'second:txtMoveTo2': '[id$="first:txtMoveTo2"]',
            'first:txtMoveTo2': '[id$="second:txtMoveTo2"]'
        };

        // Find matching pattern
        const pattern = Object.keys(inputMap).find(key => element.id.endsWith(key));
        if (pattern) {
            const otherInput = document.querySelector(inputMap[pattern]);
            if (otherInput) {
                otherInput.value = element.value;
            }
        }
    };

    /**
     * Copy value between input fields with keyboard support
     */
    const copyValue = (element, e) => {
        debugLog("Element ID:", element.id);
        debugLog("Data attr:", element.dataset.input);

        if(element.dataset.input) {
            // Use more efficient selector
            document.querySelectorAll(`[data-input="${element.dataset.input}"]`).forEach(input => input.value = element.value);
        } else {
            // Map of element IDs to their corresponding targets
            const targetMap = {
                'qaform:first:txtMoveTo2': 'qaform:second:txtMoveTo2',
                'qaform:second:txtMoveTo2': 'qaform:first:txtMoveTo2',
                'qaform:first_image:txtImageMoveTo2': 'qaform:second_image:txtImageMoveTo2'
            };

            const targetId = targetMap[element.id] || 'qaform:first_image:txtImageMoveTo2';
            const targetElement = document.getElementById(targetId);
            if (targetElement) {
                targetElement.value = element.value;
            }
        }

        const keycode = e.keyCode;

        if (keycode === 13) {
            const btn = document.querySelector('[id$="cmdImageMoveTo"]');
            if (btn) {
                btn.click();
            }
            return false;
        }
        return true;
    };

    /**
     * Setup keyboard shortcuts
     * Modernized to reduce jQuery dependency
     */
    const setupKeyboardShortcuts = () => {
        // Check if shortcut prefix is defined
        if (window.imageQAConfig?.shortcutPrefix) {
            const shortcutPrefix = window.imageQAConfig.shortcutPrefix;

            // Modern event listener approach
            document.addEventListener('keyup', (event) => {
                const isShortcutPressed = event.ctrlKey || event.metaKey || event.altKey;

                if (isShortcutPressed) {
                    switch (event.key) {
                        case 'ArrowRight':
                            const nextButton = domCache.imageButtons.next || document.getElementById("qaform:first_image:imageNext");
                            if (nextButton) {
                                nextButton.click();
                            }
                            break;
                        case 'ArrowLeft':
                            const backButton = domCache.imageButtons.back || document.getElementById("qaform:first_image:imageBack");
                            if (backButton) {
                                backButton.click();
                            }
                            break;
                    }
                }
            });
        }
    };

    /**
     * Launch fullscreen mode for an element
     */
    const launchFullScreen = (element) => {
        if (!element) return;

        if (element.requestFullscreen) {
            element.requestFullscreen();
        } else if (element.mozRequestFullScreen) { // Firefox
            element.mozRequestFullScreen();
        } else if (element.webkitRequestFullscreen) { // Chrome, Safari and Opera
            element.webkitRequestFullscreen();
        } else if (element.msRequestFullscreen) { // IE/Edge
            element.msRequestFullscreen();
        }
    };

    /**
     * Initialize image viewer with configuration
     */
    const initializeImageViewer = (config) => {
        if (!config) {
            console.error('Image viewer configuration is required');
            return;
        }

        const mediaType = config.mediaType;

        if (mediaType === "image") {
            initializeImageView(config);
        } else if (mediaType === "object") {
            initializeObjectView(config);
        } else if (mediaType === "x3dom") {
            initializeX3DOMView(config);
        }
    };

    /**
     * Initialize image view
     * Modernized to reduce jQuery dependency and use native DOM methods
     */
    const initializeImageView = async (config) => {
        // Init zoom persistence - use cached element or fallback to DOM query
        const persistenceIdElement = domCache.persistenceId || document.getElementById('persistenceId');
        let imageZoomPersistenceId = persistenceIdElement?.value;

        if (config.persistZoom && imageZoomPersistenceId && imageZoomPersistenceId.length > 0) {
            debugLog("persist image zoom with id ", imageZoomPersistenceId);
            config.viewer.global.persistenceId = imageZoomPersistenceId;
            config.viewer.global.persistZoom = true;
            config.viewer.global.persistRotation = true;
        }

        window.viewImage = new ImageView.Image(config.viewer);

        try {
            const image = await window.viewImage.load();
        } catch (error) {
            console.error('Error opening image', error);
            const mainImageElement = domCache.mainImage || document.getElementById(config.viewer.global.divId);
            if (mainImageElement) {
                mainImageElement.innerHTML = `Failed to load image: "${error}"`;
            }
        }
    };

    /**
     * Initialize 3D object view
     */
    const initializeObjectView = async (config) => {

        window.world = WorldGenerator.create(config.world);

        try {
            const object = await window.world.loadObject({
                url: config.objectUrl,
                position: { x: 0, y: 0, z: -0 },
                rotation: { x: 0, y: 0, z: 0 },
                size: 10,
                material: {
                    color: 0x44bb33
                },
                focus: true,
                onTick: function(object, time) {
                    if (object) {
                        // object.rotation.set(0,  Math.PI/180 * time, 0);
                    }
                }
            });

            window.world.render();
        } catch (error) {
            console.error("failed to load: ", error);
        }
    };

    /**
     * Initialize X3DOM view
     */
    const initializeX3DOMView = (config) => {
        const mainImage = domCache.mainImage || document.getElementById('mainImage');

        new X3DLoader().load(mainImage, config.objectUrl,
            async function() {
                debugLog("X3DOM loaded successfully");
            },
            function() {
                debugLog("X3DOM loading progress");
            },
            async function(error) {
                console.error("X3DOM error", error);
            });
    };

    /**
     * Free JavaScript resources
     */
    const freeJSResources = (data) => {
        if(!data || data.status === 'begin') {
            if(window.viewImage) {
                debugLog("closing OpenSeadragon viewer");
                window.viewImage.close();
                window.viewImage = null; // Explicitly clear reference
            }
            if(window.world) {
                debugLog("disposing 3d scene");
                window.world.dispose();
                window.world = null; // Explicitly clear reference
            }
        }
    };

    /**
     * Confirmation dialog handler
     */
    const setupConfirmationHandlers = () => {
        // Use event delegation for better performance
        document.addEventListener('click', (event) => {
            const target = event.target.closest('[data-confirm]');
            if (target && target.hasAttribute('data-confirm')) {
                const message = target.getAttribute('data-confirm') || 'Are you sure?';
                if (!confirm(message)) {
                    event.preventDefault();
                    event.stopPropagation();
                    return false;
                }
            }
        });
    };

    /**
     * Image loading handler for thumbnails
     * Uses Intersection Observer for lazy loading
     */
    const setupLazyImageLoading = () => {
        const canvases = document.querySelectorAll('.thumb-canvas');

        if ('IntersectionObserver' in window) {
            const imageObserver = new IntersectionObserver((entries, observer) => {
                entries.forEach(entry => {
                    if (entry.isIntersecting) {
                        const canvas = entry.target;
                        loadThumbnailImage(canvas);
                        observer.unobserve(canvas);
                    }
                });
            });

            // Observe all thumbnail canvases
            canvases.forEach(canvas => {
                imageObserver.observe(canvas);
            });
        } else {
            // Fallback for older browsers
            canvases.forEach(loadThumbnailImage);
        }
    };

    /**
     * Load individual thumbnail image
     */
    const loadThumbnailImage = (canvas) => {
        if (!canvas.dataset.image_small) return;

        const img = new Image();
        img.onload = function() {
            const ctx = canvas.getContext('2d');
            canvas.width = this.width;
            canvas.height = this.height;
            ctx.drawImage(this, 0, 0);
        };
        img.src = canvas.dataset.image_small;
    };

    // Initialize when DOM is ready
    domReady(() => {
        // Initialize DOM cache first
        initializeDOMCache();

        // Then initialize other components
        loadThumbnails();
        setupAjaxEvents();
        setupKeyboardShortcuts();
        setupConfirmationHandlers();
        setupLazyImageLoading();
    });

    // Expose necessary functions globally for JSF callbacks
    window.freeJSResources = freeJSResources;
    window.initializeImageViewer = initializeImageViewer;
    window.copyValue = copyValue;
    window.clickGoToImageButton = clickGoToImageButton;
    window.launchFullScreen = launchFullScreen;
    window.loadThumbnails = loadThumbnails;

})(); // End IIFE

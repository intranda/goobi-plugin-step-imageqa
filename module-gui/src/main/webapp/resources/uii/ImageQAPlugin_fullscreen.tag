<fullscreen>
	<div id="mainImage">
	    <div id="ajaxloader_image">
	        <img src="template/img/goobi/ajaxloader1.gif" />
	    </div>
	</div>
	<div id="zoomSliderLabel" class="fullscreen-control">
            <input aria-label="{msgs.sizeOfImages}"></input><span>%</span>
    </div>

    <div
		class="fullscreen-control"
		style="top:0; right:0;">
		<button class="btn btn-outline" onclick={leave} aria-label="{msgs.imageDefaultDisplay}" title="{msgs.imageDefaultDisplay}" >
			<i class="fa fa-close"></i>
		</button>
	</div>
	<!-- previous image -->
	<button id="imageBack" class="btn btn-outline fullscreen-control" aria-label="{msgs.lw_previousImage}" title="{msgs.lw_previousImage}"
		style="top:50vh; left:0;"
		onclick={previousImage}>
		<i class="fa fa-angle-left"></i>
	</button>

	<!-- next image -->
	<button id="imageNext" class="btn btn-outline fullscreen-control" aria-label="{msgs.lw_nextImage}" title="{msgs.lw_nextImage}"
		style="top:50vh; right:0px;"
		onclick={nextImage}>
		<i class="fa fa-angle-right"></i>
	</button>

	<!-- file name -->
	<h1 style="margin: 0px;">
		<span class="fullscreen-control"
			style="bottom:0; left:0;">
			{currentImage().imageName}
		</span>
	</h1>

	<!-- image number -->
	<div
		class="fullscreen-control"
		style="bottom:0; right:0;">
	    <span id="txtImageMoveTo1" class="font-white"
	    	if={!showPagenumberInput}
	        onclick={showAndFocusImageNumberInput}>
			{imageIndex +1} {msgs.von}  {allImages.length}
        </span>
		<!-- jump to page -->
        <input value={currentImageNumber} if={showPagenumberInput}
            id="txtImageMoveTo2"
            ref="txtImageMoveInput"
            onblur={jumpToPage}
            onkeyup={pagenumerInputKeyup}>
	</div>

<script>

this.msgs = {};
this.imageIndex = 0;
this.allImages = [];
this.infoJsonCache = {};

this.on('beforeMount', () => {
	this.currentImage = {};
});

this.on("mount", () => {
	console.log("mounting fullscreen", this.opts);
	this.useTiles = this.opts.useTiles;
	this.imageSizes = this.opts.imageSizes;
	this.tileSizes = this.opts.tileSizes;
	this.allImages = JSON.parse(this.opts.allImages);
	this.update();
	this.lang = this.opts.lang;
	this.imageIndex = this.opts.startImage;
	this.loadMessages();
	this.loadInitialImage().then(() => {
		this.loadInfoJsonCache(1, false);
	});
	if(this.opts.noShortcutPrefix) {
		$(document).bind('keyup', 'left', e => this.shortcutPrevious(e));
		$(document).bind('keyup', 'right', e => this.shortcutNext(e));
	} else {
		$(document).bind('keyup', this.opts.shortcutPrefix+'+left', this.shortcutPrevious);
		$(document).bind('keyup', this.opts.shortcutPrefix+'+right', this.shortcutNext);
	}
})

loadMessages() {
	//TODO
	fetch(`/goobi/api/messages/${this.lang}`, {
          method: 'GET',
          credentials: 'same-origin'
      }).then(resp => {
        resp.json().then(json => {
          this.msgs = json;
          this.update();
        })
      })
}

shortcutPrevious(e) {
	e.stopPropagation();
	this.previousImage();
	this.update();
}

shortcutNext(e) {
	e.stopPropagation();
	this.nextImage();
	this.update();
}

showAndFocusImageNumberInput() {
	this.showPagenumberInput = true;
	this.update();
	this.refs.txtImageMoveInput.value = this.imageIndex+1;
	this.refs.txtImageMoveInput.focus();
	this.refs.txtImageMoveInput.select();
}

jumpToPage(e) {
	var newValue = parseInt(e.target.value.trim()) - 1;
	if(newValue < 0 || newValue >= this.allImages.length || newValue == this.imageIndex) {
		return;
	}
	this.imageIndex = newValue;
	this.showPagenumberInput = false;
	this.update();
	this.loadCurrentImage();
}


pagenumerInputKeyup(e) {
	if(e.key == "Enter") {
		this.jumpToPage(e);
	}
}

currentImage() {
	if(!this.allImages) {
		return {};
	}
	return this.allImages[this.imageIndex];
}

nextImage() {
	this.imageIndex = Math.min(this.allImages.length-1, this.imageIndex+1);
	this.loadCurrentImage();
}

previousImage() {
	this.imageIndex = Math.max(0, this.imageIndex-1);
	this.loadCurrentImage();
}

loadCurrentImage() {
	var infoJson = this.infoJsonCache[this.imageIndex];
	if(infoJson) {
		this.viewImage.setTileSource(infoJson)
		.then(() => {
			this.viewImage.controls.persistence = new ImageView.Controls.Persistence(this.viewImage.config, this.viewImage);
		})
	} else {
		this.viewImage.setTileSource(this.currentImage().url)
		.then(() => {
			this.viewImage.controls.persistence = new ImageView.Controls.Persistence(this.viewImage.config, this.viewImage);
		})
	}
}

loadInfoJsonCache(start) {
	var promises = [];
	for(let i=start; i<start+5; i++) {
		if(this.allImages[i]) {
			promises.push(
				fetch(this.allImages[i].url)
					.then((response) => {
						response.json().then((infoJson) => {
	// 						infoJson.sizes = this.imageSizes;
							this.infoJsonCache[i] = infoJson;
						}).catch(err => {
							console.log(err);
						})
					}).catch(err => {
						console.log(err);
					})
			);
		}
	}
	Promise.all(promises).then(responses => {
		var nextStart = start+5;
		if(nextStart > this.allImages.length) {
			nextStart = this.allImages.length - 6;
			stopAfterNext = true;
		}
		if(nextStart == this.allImages.length) {
			return;
		}
		this.loadInfoJsonCache(nextStart);
	})
}

leave() {
	document.querySelector("#actions\\:leave").click();
}

loadInitialImage() {
	return new Promise((resolve, reject) => {
		fetch(this.currentImage().url).then(response => {
			response.json().then(infoJson => {
				console.log("use tiles:", this.useTiles);
// 				infoJson.sizes = this.imageSizes;
				var configViewer = {
				    global: {
				    	divId: 'mainImage',
				        useTiles: this.useTiles,
				        footerHeight: 0,
				        adaptContainerHeight: false,
				        zoomSlider: "#zoomSlider",
				        zoomSliderHandle: "#zoomSlider .zoom-slider-handle",
				        zoomSliderLabel: "#zoomSliderLabel input",
// 			            imageSizes: this.imageSizes,
			            tileSizes: this.tileSizes,
				    },
				    image: {
				        mimeType: "image/jpeg",
				        tileSource: infoJson
				    }
				};

				let imageZoomPersistenzeId = $( '#persistenceId' ).val();
                if(this.opts.persistZoom && imageZoomPersistenzeId && imageZoomPersistenzeId.length > 0) {
                    console.log("persist image zoom with id ", imageZoomPersistenzeId);
                    configViewer.global.persistenceId = imageZoomPersistenzeId;
                    configViewer.global.persistZoom =  true;
                    configViewer.global.persistRotation = true;
                }

			    this.viewImage = new ImageView.Image( configViewer );
			    this.viewImage.load()
			    .then((image) => {
				    this.viewImage.viewer.blendTime = 0.0;
			    	console.log(this.viewImage.viewer.drawer.getOpacity())
			        image.onFirstTileLoaded()
			        .then(function() {
			        	$('#ajaxloader_image').fadeOut(800);
			        	resolve();
			        })
			        .catch(function() {
			            $('#ajaxloader_image').fadeOut(800);
			            reject();
			        })
			    })
			    .catch(function(error){
			        console.error( 'Error opening image', error );
			        $('#ajaxloader_image').fadeOut(800);
			        $('#' + configViewer.global.divId).html( 'Failed to load image: "' + error + '"' );
			        reject();
			    });
			})

		})
	})
}


</script>
</fullscreen>
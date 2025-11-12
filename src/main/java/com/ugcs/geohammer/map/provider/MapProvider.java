package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;

import com.ugcs.geohammer.model.MapField;
import org.jspecify.annotations.Nullable;

public interface MapProvider {

	@Nullable
	BufferedImage loadimg(MapField field);
	
	int getMaxZoom();

	default int getMinZoom() {
		return 2;
	};
	
}

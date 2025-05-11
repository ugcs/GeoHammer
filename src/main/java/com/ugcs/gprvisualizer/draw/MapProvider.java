package com.ugcs.gprvisualizer.draw;

import java.awt.image.BufferedImage;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import org.jspecify.annotations.Nullable;

public interface MapProvider {

	@Nullable
	BufferedImage loadimg(MapField field);
	
	int getMaxZoom();

	default int getMinZoom() {
		return 1;
	};
	
}

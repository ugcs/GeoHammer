package com.ugcs.gprvisualizer.draw.map;

import java.awt.image.BufferedImage;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.gpr.Model;
import org.jspecify.annotations.Nullable;

public interface MapProvider {

	String id();

	String name();

	default boolean isDefault() { return false; }

	default void onSelect(Model model) {
		model.getMapField().setMapProvider(this);
	}

	@Nullable
	BufferedImage loading(MapField field);
	
	int getMaxZoom();

	default int getMinZoom() {
		return 1;
	}
	
}

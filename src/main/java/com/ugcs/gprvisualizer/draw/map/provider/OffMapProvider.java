package com.ugcs.gprvisualizer.draw.map.provider;

import java.awt.image.BufferedImage;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.draw.map.MapProvider;
import com.ugcs.gprvisualizer.gpr.Model;
import org.jspecify.annotations.Nullable;

public class OffMapProvider  implements MapProvider {
	@Override
	public String id() {
		return "off";
	}

	@Override
	public String name() {
		return "turn off";
	}

	@Override
	public void onSelect(Model model) {
		model.getMapField().setMapProvider(null);
	}

	@Override
	public @Nullable BufferedImage loading(MapField field) {
		return null;
	}

	@Override
	public int getMaxZoom() { return 1; }
}

package com.ugcs.gprvisualizer.draw;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import org.jspecify.annotations.Nullable;

public class HereMapProvider implements MapProvider {

	private static final String DEFAULT_API_KEY = "rE86EaZdU1Dz85CegYxNt0cNJXcFB-xFGUuvbn22Gds";
	private final String apiKey;

	HereMapProvider(String apiKey) {
		this.apiKey = apiKey != null ? apiKey : DEFAULT_API_KEY;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}

	@Nullable
	@Override
	public BufferedImage loadimg(MapField field) {
		System.out.println(field.getZoom());
		if (field.getZoom() > getMaxZoom()) {
			field.setZoom(getMaxZoom());
		}
		
		BufferedImage img = null;
		
		LatLon midlPoint = field.getSceneCenter();
		if (midlPoint == null) {
			return img;
		}

		DecimalFormat df = new DecimalFormat("#.0000000", DecimalFormatSymbols.getInstance(Locale.US));
		try {
			String url = String.format("https://image.maps.hereapi.com/mia/v3/base/mc/center:%s,%s;zoom=%d/1200x1200/png"
							+ "?apiKey=%s"
							+ "&style=explore.satellite.day",
					df.format(midlPoint.getLatDgr()),
					df.format(midlPoint.getLonDgr()),
					field.getZoom(),
					apiKey);

			System.out.println(url);

			System.setProperty("java.net.useSystemProxies", "true");
			img = ImageIO.read(new URI(url).toURL());

		} catch (IOException | URISyntaxException e) {
			System.err.println(e.getMessage());
		}
		return img;
	}
}
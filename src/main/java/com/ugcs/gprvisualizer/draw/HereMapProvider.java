package com.ugcs.gprvisualizer.draw;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

import javax.imageio.ImageIO;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.SphericalMercator;
import org.apache.commons.collections.EnumerationUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HereMapProvider implements MapProvider {

	private static final String DEFAULT_API_KEY = "wlY4dBPWM4fiRHa54nVigCg5cyYVnA-5mSPuR2Xdz3A";
	private static final Logger log = LoggerFactory.getLogger(HereMapProvider.class);
	private final String apiKey;

	private RequestParams previousRequestParams = null;

	HereMapProvider(String apiKey) {
		this.apiKey = apiKey != null ? apiKey : DEFAULT_API_KEY;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}

	@Nullable
	@Override
	public BufferedImage loading(MapField field) {
		log.debug("Zoom level: {}", field.getZoom());

		if (field.getZoom() > getMaxZoom()) {
			field.setZoom(getMaxZoom());
		}

		LatLon midlPoint = field.getSceneCenter();
		if (previousRequestParams != null && midlPoint != null && previousRequestParams.zoom == field.getZoom() &&
				previousRequestParams.centerPoint.getDistance(midlPoint) < 1.0) {
			return previousRequestParams.img;
		}

		BufferedImage img = null;

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

			log.debug("Here Maps URL: {}", url);

			System.setProperty("java.net.useSystemProxies", "true");
			img = ImageIO.read(new URI(url).toURL());
			previousRequestParams = new RequestParams(img, midlPoint, field.getZoom());
		} catch (IOException | URISyntaxException e) {
			log.error("Failed to load map image", e);
		}
		return img;
	}

	record RequestParams(@Nullable BufferedImage img, LatLon centerPoint, int zoom) { }
}
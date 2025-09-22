package com.ugcs.gprvisualizer.draw.map.provider;

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
import com.ugcs.gprvisualizer.draw.map.MapProvider;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import org.jspecify.annotations.Nullable;

public class HereMapProvider implements MapProvider {

	private static final String DEFAULT_API_KEY = "";

	private final PrefSettings prefSettings;

	public HereMapProvider(PrefSettings prefSettings) {
		this.prefSettings = prefSettings;
	}

	private String getApiKey() {
		String apiKey = prefSettings.getSetting("maps", "here_api_key");
		return apiKey != null ? apiKey : DEFAULT_API_KEY;
	}

	@Override
	public String id() {
		return "here";
	}

	@Override
	public String name() {
		return "here.com";
	}

	@Override
	public boolean isDefault() {
		return true;
	}

	@Nullable
	@Override
	public BufferedImage loading(MapField field) {
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
					field.getZoomInt(),
					getApiKey());

			System.out.println(url);

			System.setProperty("java.net.useSystemProxies", "true");
			img = ImageIO.read(new URI(url).toURL());

		} catch (IOException | URISyntaxException e) {
			System.err.println(e.getMessage());
		}
		return img;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}
}
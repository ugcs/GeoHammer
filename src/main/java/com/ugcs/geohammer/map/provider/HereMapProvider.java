package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.ugcs.geohammer.math.GoogleCoordUtils;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.util.Strings;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HereMapProvider implements MapProvider {

	private static final Logger log = LoggerFactory.getLogger(HereMapProvider.class);

	private static final int FETCH_TILE_SIZE = 1200;

	private static final String DEFAULT_API_KEY = "";

	private final String apiKey;

	public HereMapProvider(String apiKey) {
		this.apiKey = apiKey != null ? apiKey : DEFAULT_API_KEY;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}

	@Nullable
	@Override
	public BufferedImage loadimg(MapField field) {
		if (Strings.isNullOrEmpty(apiKey)) {
			return null;
		}

		int intZoom = Math.clamp((int)field.getZoom(), getMinZoom(), getMaxZoom());
		field.setZoom(intZoom);

		LatLon center = field.getSceneCenter();
		if (center == null) {
			return null;
		}

		int tileSize = getTileSize(intZoom, FETCH_TILE_SIZE);
		center = getActualTileCenter(center, intZoom, tileSize);
		field.setSceneCenter(center);

		DecimalFormat coordinatesFormat
				= new DecimalFormat("0.0000000", DecimalFormatSymbols.getInstance(Locale.US));

		BufferedImage tile = null;
		try {
			String url = String.format(
					"https://image.maps.hereapi.com/mia/v3/base/mc/center:%s,%s;zoom=%d/%dx%d/png"
							+ "?apiKey=%s"
							+ "&style=explore.satellite.day",
					coordinatesFormat.format(center.getLatDgr()),
					coordinatesFormat.format(center.getLonDgr()),
					intZoom,
					tileSize,
					tileSize,
					apiKey
			);
			log.info("Fetching map tile {}", url);
			System.setProperty("java.net.useSystemProxies", "true");
			tile = ImageIO.read(new URI(url).toURL());
		} catch (IOException | URISyntaxException e) {
			log.error("Cannot fetch map tile", e);
		}
		return tile;
	}

	private int getTileSize(int zoom, int preferredTileSize) {
		int scale = 1 << zoom;
		int mapSize = scale * GoogleCoordUtils.TILE_SIZE;
		return Math.min(mapSize, preferredTileSize);
	}

	private LatLon getActualTileCenter(LatLon center, int zoom, int tileSize) {
		double scale = 1 << zoom;
		double mapSize = GoogleCoordUtils.TILE_SIZE * scale;

		Point2D googleProjected = GoogleCoordUtils.project(center);
		// here tile size equals to google tile size, otherwise
		// rescale here
		Point2D projected = new Point2D(
				scale * googleProjected.getX(),
				scale * googleProjected.getY()
		);
		double mapX = projected.getX();
		double mapY = projected.getY();

		double half = 0.5 * tileSize;
		double clampedX = Math.max(half, Math.min(mapX, mapSize - half));
		double clampedY = Math.max(half, Math.min(mapY, mapSize - half));

		if (mapX == clampedX && mapY == clampedY) {
			return center;
		}
		double scaleInv = 1.0 / scale;
		return GoogleCoordUtils.unproject(new Point2D(
				scaleInv * clampedX,
				scaleInv * clampedY));
	}
}
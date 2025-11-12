package com.ugcs.geohammer.map.provider;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;

import com.ugcs.geohammer.math.GoogleCoordUtils;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleMapProvider implements MapProvider {

	private static final Logger log = LoggerFactory.getLogger(GoogleMapProvider.class);

	private static final int MAP_IMAGE_SIZE = 1280;

	@Override
	public int getMaxZoom() {
		return 20;
	}

	@Nullable
	@Override
	public BufferedImage loadimg(MapField field) {
		int intZoom = Math.clamp((int)field.getZoom(), getMinZoom(), getMaxZoom());
		field.setZoom(intZoom);

		BufferedImage tile = null;
		System.setProperty("java.net.useSystemProxies", "true");
		try {
			tile = createCenteredMapImage(field, intZoom);
		} catch (IOException | URISyntaxException e) {
			log.error("Cannot fetch map tile", e);
		}
		return tile;
	}

	private BufferedImage createCenteredMapImage(MapField field, int zoom)
			throws IOException, URISyntaxException {
		LatLon mapCenter = field.getSceneCenter();
		if (mapCenter == null) {
			return null;
		}

		Tile centralTile = latLonToTile(mapCenter, zoom);
		LatLon centralTileCenter = getTileCenter(centralTile);
		// reposition map to a center of the returned image
		field.setSceneCenter(centralTileCenter);

		BufferedImage combinedImage = new BufferedImage(MAP_IMAGE_SIZE, MAP_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = combinedImage.createGraphics();

		// drawing offsets for the tiles
		Point2D drawOffset = new Point2D(
				MAP_IMAGE_SIZE / 2.0 - GoogleCoordUtils.TILE_SIZE / 2.0,
				MAP_IMAGE_SIZE / 2.0 - GoogleCoordUtils.TILE_SIZE / 2.0);

		// num of margin tiles: how many side tiles to load
		int m = ((MAP_IMAGE_SIZE / GoogleCoordUtils.TILE_SIZE) - 1) / 2;

		// draw the tiles around the central tile
		for (int dx = -m; dx <= m; dx++) {
			for (int dy = -m; dy <= m; dy++) {
				BufferedImage tileImage = getTileImage(new Tile(
						centralTile.x() + dx,
						centralTile.y() + dy,
						centralTile.z()));
				if (tileImage != null) {
					g2d.drawImage(
							tileImage,
							(int)drawOffset.getX() + dx * GoogleCoordUtils.TILE_SIZE,
							(int)drawOffset.getY() + dy * GoogleCoordUtils.TILE_SIZE,
							null);
				}
			}
		}

		g2d.dispose();
		return combinedImage;
	}

	private BufferedImage getTileImage(Tile tile) throws IOException, URISyntaxException {
		int numTiles = 1 << tile.z();
		if (tile.x() < 0 || tile.x() >= numTiles
				|| tile.y() < 0 || tile.y() >= numTiles) {
			return null;
		}

		String urlPattern = "https://mt1.google.com/vt/lyrs=y&x=%s&y=%s&z=%s";
		String url = String.format(urlPattern, tile.x(), tile.y(), tile.z());

		// Check if the image already exists in the temporary folder
		String tempFolderPath = System.getProperty("java.io.tmpdir");
		String imageFileName = String.format("tile_%s_%s_%s.png", tile.x(), tile.y(), tile.z());
		String imagePath = tempFolderPath + File.separator + imageFileName;
		File imageFile = new File(imagePath);

		BufferedImage tileImage;
		if (imageFile.exists()) {
			// If the image already exists, load it from the temporary folder
			tileImage = ImageIO.read(imageFile);
		} else {
			// If the image doesn't exist, download it and save it to the temporary folder
			log.info("Fetching map tile {}", url);
			tileImage = ImageIO.read(new URI(url).toURL());
			ImageIO.write(tileImage, "png", imageFile);
		}

		return tileImage;
	}

	private Tile latLonToTile(LatLon latLon, int zoom) {
		double lat = latLon.getLatDgr();
		double lon = latLon.getLonDgr();

		int x = (int) Math.floor((lon + 180) / 360 * Math.pow(2, zoom));
		int y = (int) Math.floor(
				(1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI)
						/ 2 * Math.pow(2, zoom));
		return new Tile(x, y, zoom);
	}

	private LatLon getTileCenter(Tile tile) {
		Point2D tileCenter = new Point2D(
				GoogleCoordUtils.TILE_SIZE * (tile.x() + 0.5),
				GoogleCoordUtils.TILE_SIZE * (tile.y() + 0.5));
		return GoogleCoordUtils.latLonFromPoint(tileCenter, tile.z());
	}

	record Tile(int x, int y, int z) {
		Tile(int x, int y, int z) {
			this.x = Math.max(0, x);
			this.y = Math.max(0, y);
			this.z = z;
		}
	}
}
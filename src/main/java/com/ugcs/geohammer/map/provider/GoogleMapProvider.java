package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleMapProvider extends TileMapProvider {

	private static final Logger log = LoggerFactory.getLogger(GoogleMapProvider.class);

	private static final ExecutorService GOOGLE_TILE_FETCH_POOL = Executors.newFixedThreadPool(
			TILE_COUNT,
			r -> {
				Thread t = new Thread(r, "google-tile-fetch");
				t.setDaemon(true);
				return t;
			});

	@Override
	protected ExecutorService tileFetchPool() {
		return GOOGLE_TILE_FETCH_POOL;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}

	@Nullable
	@Override
	protected BufferedImage fetchTile(Tile tile) {
		int numTiles = 1 << tile.z();
		if (tile.x() < 0 || tile.x() >= numTiles
				|| tile.y() < 0 || tile.y() >= numTiles) {
			return null;
		}

		String url = String.format("https://mt1.google.com/vt/lyrs=y&x=%s&y=%s&z=%s",
				tile.x(), tile.y(), tile.z());

		String tempFolderPath = System.getProperty("java.io.tmpdir");
		String imageFileName = String.format("tile_%s_%s_%s.png", tile.x(), tile.y(), tile.z());
		File imageFile = new File(tempFolderPath + File.separator + imageFileName);

		if (imageFile.exists()) {
			try {
				return ImageIO.read(imageFile);
			} catch (IOException e) {
				log.warn("Corrupt cached tile {}, re-fetching", imageFile);
			}
		}

		try {
			log.info("Fetching map tile {}", url);
			BufferedImage tileImage = ImageIO.read(new URI(url).toURL());
			if (tileImage != null) {
				writeToCache(tileImage, imageFile);
			}
			return tileImage;
		} catch (IOException | URISyntaxException e) {
			log.error("Cannot fetch map tile", e);
			return null;
		}
	}
}

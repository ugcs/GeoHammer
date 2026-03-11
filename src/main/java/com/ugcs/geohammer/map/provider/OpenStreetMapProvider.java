package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenStreetMapProvider extends TileMapProvider {

	private static final Logger log = LoggerFactory.getLogger(OpenStreetMapProvider.class);

	private static final String[] TILE_SUBDOMAINS = {"a", "b", "c"};

	private static final String TILE_URL_PATTERN = "https://%s.tile.openstreetmap.org/%d/%d/%d.png";

	private static final int CONNECT_TIMEOUT_MS = 5_000;

	private static final int READ_TIMEOUT_MS = 10_000;

	private final String userAgent;

	// OSM tile usage policy allows max 2 parallel connections per client
	private static final ExecutorService OSM_TILE_FETCH_POOL = Executors.newFixedThreadPool(
			2,
			r -> {
				Thread t = new Thread(r, "osm-tile-fetch");
				t.setDaemon(true);
				return t;
			});

	public OpenStreetMapProvider(String appVersion) {
		this.userAgent = "GeoHammer/" + appVersion + " (https://github.com/ugcs/geohammer)";
	}

	@Override
	protected ExecutorService tileFetchPool() {
		return OSM_TILE_FETCH_POOL;
	}

	@Override
	public int getMaxZoom() {
		return 19;
	}

	@Nullable
	@Override
	protected BufferedImage fetchTile(Tile tile) {
		int numTiles = 1 << tile.z();
		if (tile.x() < 0 || tile.x() >= numTiles
				|| tile.y() < 0 || tile.y() >= numTiles) {
			return null;
		}

		String tempDir = System.getProperty("java.io.tmpdir");
		String fileName = String.format("osm_tile_%d_%d_%d.png", tile.x(), tile.y(), tile.z());
		File cacheFile = new File(tempDir + File.separator + fileName);

		if (cacheFile.exists()) {
			try {
				return ImageIO.read(cacheFile);
			} catch (IOException e) {
				log.warn("Corrupt cached tile {}, re-fetching", cacheFile);
			}
		}

		String subdomain = TILE_SUBDOMAINS[(tile.x() + tile.y()) % TILE_SUBDOMAINS.length];
		String url = String.format(TILE_URL_PATTERN, subdomain, tile.z(), tile.x(), tile.y());
		log.info("Fetching OSM tile {}", url);

		try {
			HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
			conn.setRequestProperty("User-Agent", userAgent);
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);

			int responseCode = conn.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK) {
				log.warn("OSM tile server returned HTTP {} for {}", responseCode, url);
				return null;
			}

			BufferedImage tileImage;
			try (var in = conn.getInputStream()) {
				tileImage = ImageIO.read(in);
			}
			if (tileImage != null) {
				writeToCache(tileImage, cacheFile);
			}
			return tileImage;
		} catch (IOException | URISyntaxException e) {
			log.warn("Failed to fetch OSM tile {}", url, e);
			return null;
		}
	}
}

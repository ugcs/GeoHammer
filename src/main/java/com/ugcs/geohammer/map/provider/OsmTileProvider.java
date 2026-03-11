package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsmTileProvider implements XyzTileProvider {

	private static final Logger log = LoggerFactory.getLogger(OsmTileProvider.class);

	private static final String[] TILE_SUBDOMAINS = {"a", "b", "c"};

	private static final String TILE_URL_PATTERN = "https://%s.tile.openstreetmap.org/%d/%d/%d.png";

	private static final int CONNECT_TIMEOUT_MS = 5_000;

	private static final int READ_TIMEOUT_MS = 10_000;

	private final String userAgent;

	public OsmTileProvider(String appVersion) {
		this.userAgent = "GeoHammer/" + appVersion + " (https://github.com/ugcs/geohammer)";
	}

	// OSM tile usage policy allows max 2 parallel connections per client
	@Override
	public int maxConcurrentRequests() {
		return 2;
	}

	@Override
	public int getMaxZoom() {
		return 19;
	}

	@Override
	public String getCachePrefix() {
		return "osm_tile";
	}

	@Nullable
	@Override
	public BufferedImage fetchTile(XyzTile tile) {
		String subdomain = TILE_SUBDOMAINS[(tile.x() + tile.y()) % TILE_SUBDOMAINS.length];
		String url = String.format(TILE_URL_PATTERN, subdomain, tile.z(), tile.x(), tile.y());
		log.debug("Fetching OSM tile {}", url);

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

			try (var in = conn.getInputStream()) {
				return ImageIO.read(in);
			}
		} catch (IOException | URISyntaxException e) {
			log.warn("Failed to fetch OSM tile {}", url, e);
			return null;
		}
	}
}

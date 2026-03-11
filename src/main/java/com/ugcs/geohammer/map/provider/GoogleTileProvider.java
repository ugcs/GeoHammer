package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleTileProvider implements XyzTileProvider {

	private static final Logger log = LoggerFactory.getLogger(GoogleTileProvider.class);

	@Override
	public int maxConcurrentRequests() {
		return 5;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}

	@Override
	public String getCachePrefix() {
		return "tile";
	}

	@Nullable
	@Override
	public BufferedImage fetchTile(XyzTile tile) {
		String url = String.format("https://mt1.google.com/vt/lyrs=y&x=%s&y=%s&z=%s",
				tile.x(), tile.y(), tile.z());
		log.debug("Fetching map tile {}", url);
		try {
			return ImageIO.read(new URI(url).toURL());
		} catch (IOException | URISyntaxException e) {
			log.error("Cannot fetch map tile", e);
			return null;
		}
	}
}

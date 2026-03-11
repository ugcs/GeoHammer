package com.ugcs.geohammer.map.provider;

import java.awt.image.BufferedImage;

import org.jspecify.annotations.Nullable;

public interface XyzTileProvider {

	int maxConcurrentRequests();

	int getMaxZoom();

	String getCachePrefix();

	@Nullable
	BufferedImage fetchTile(XyzTile tile);
}

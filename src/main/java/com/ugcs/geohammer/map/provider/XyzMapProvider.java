package com.ugcs.geohammer.map.provider;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import javax.imageio.ImageIO;

import com.ugcs.geohammer.math.GoogleCoordUtils;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XyzMapProvider implements MapProvider, Closeable {

	private static final Logger log = LoggerFactory.getLogger(XyzMapProvider.class);

	private static final int MAP_IMAGE_SIZE = 1280;

	private static final int GRID_SIZE = MAP_IMAGE_SIZE / GoogleCoordUtils.TILE_SIZE;

	private static final int TILE_COUNT = GRID_SIZE * GRID_SIZE;

	private final XyzTileProvider tileProvider;

	private final ExecutorService tilePool;

	public XyzMapProvider(XyzTileProvider tileProvider) {
		this.tileProvider = tileProvider;
		this.tilePool = Executors.newFixedThreadPool(
				tileProvider.maxConcurrentRequests(),
				Thread.ofVirtual().factory());
	}

	@Override
	public void close() {
		tilePool.shutdown();
	}

	@Override
	public int getMaxZoom() {
		return tileProvider.getMaxZoom();
	}

	@Nullable
	@Override
	public BufferedImage loadimg(MapField field) {
		int intZoom = Math.clamp((int) field.getZoom(), getMinZoom(), getMaxZoom());
		field.setZoom(intZoom);
		System.setProperty("java.net.useSystemProxies", "true");
		return createCenteredMapImage(field, intZoom);
	}

	@Nullable
	private BufferedImage createCenteredMapImage(MapField field, int zoom) {
		LatLon mapCenter = field.getSceneCenter();
		if (mapCenter == null) {
			return null;
		}

		XyzTile centralTile = latLonToTile(mapCenter, zoom);
		field.setSceneCenter(getTileCenter(centralTile));

		int m = (GRID_SIZE - 1) / 2;

		record TileFetch(int dx, int dy, CompletableFuture<BufferedImage> future) {}
		List<TileFetch> fetches = new ArrayList<>(TILE_COUNT);
		for (int dx = -m; dx <= m; dx++) {
			for (int dy = -m; dy <= m; dy++) {
				XyzTile tile = new XyzTile(centralTile.x() + dx, centralTile.y() + dy, centralTile.z());
				CompletableFuture<BufferedImage> future;
				try {
					future = CompletableFuture.supplyAsync(() -> fetchTile(tile), tilePool);
				} catch (RejectedExecutionException ignored) {
					// Pool was shut down mid-render due to a provider switch; skip this tile.
					future = CompletableFuture.completedFuture(null);
				}
				fetches.add(new TileFetch(dx, dy, future));
			}
		}

		BufferedImage combinedImage = new BufferedImage(MAP_IMAGE_SIZE, MAP_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = combinedImage.createGraphics();
		Point2D drawOffset = new Point2D(
				MAP_IMAGE_SIZE / 2.0 - GoogleCoordUtils.TILE_SIZE / 2.0,
				MAP_IMAGE_SIZE / 2.0 - GoogleCoordUtils.TILE_SIZE / 2.0);

		for (TileFetch fetch : fetches) {
			try {
				BufferedImage tileImage = fetch.future().get();
				if (tileImage != null) {
					g2d.drawImage(
							tileImage,
							(int) drawOffset.getX() + fetch.dx() * GoogleCoordUtils.TILE_SIZE,
							(int) drawOffset.getY() + fetch.dy() * GoogleCoordUtils.TILE_SIZE,
							null);
				}
			} catch (InterruptedException e) {
				fetches.forEach(f -> f.future().cancel(true));
				Thread.currentThread().interrupt();
				break;
			} catch (ExecutionException e) {
				log.warn("Tile fetch failed", e.getCause());
			}
		}

		g2d.dispose();
		return combinedImage;
	}

	@Nullable
	private BufferedImage fetchTile(XyzTile tile) {
		int numTiles = 1 << tile.z();
		if (tile.x() < 0 || tile.x() >= numTiles
				|| tile.y() < 0 || tile.y() >= numTiles) {
			return null;
		}

		String tempDir = System.getProperty("java.io.tmpdir");
		String fileName = String.format("%s_%d_%d_%d.png",
				tileProvider.getCachePrefix(), tile.x(), tile.y(), tile.z());
		File cacheFile = new File(tempDir + File.separator + fileName);

		if (cacheFile.exists()) {
			try {
				return ImageIO.read(cacheFile);
			} catch (IOException e) {
				log.warn("Corrupt cached tile {}, re-fetching", cacheFile);
			}
		}

		BufferedImage image = tileProvider.fetchTile(tile);
		if (image != null) {
			writeToCache(image, cacheFile);
		}
		return image;
	}

	private void writeToCache(BufferedImage image, File target) {
		File tmp = new File(target.getPath() + ".tmp");
		try {
			if (!ImageIO.write(image, "png", tmp)) {
				log.warn("No PNG writer available, tile will not be cached");
				return;
			}
			try {
				Files.move(tmp.toPath(), target.toPath(),
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			log.warn("Failed to cache tile {}", target, e);
			tmp.delete();
		}
	}

	private XyzTile latLonToTile(LatLon latLon, int zoom) {
		double lat = latLon.getLatDgr();
		double lon = latLon.getLonDgr();
		int x = (int) Math.floor((lon + 180) / 360 * Math.pow(2, zoom));
		int y = (int) Math.floor(
				(1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI)
						/ 2 * Math.pow(2, zoom));
		return new XyzTile(x, y, zoom);
	}

	private LatLon getTileCenter(XyzTile tile) {
		Point2D tileCenter = new Point2D(
				GoogleCoordUtils.TILE_SIZE * (tile.x() + 0.5),
				GoogleCoordUtils.TILE_SIZE * (tile.y() + 0.5));
		return GoogleCoordUtils.latLonFromPoint(tileCenter, tile.z());
	}
}

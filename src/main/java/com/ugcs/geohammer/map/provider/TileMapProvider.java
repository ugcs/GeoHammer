package com.ugcs.geohammer.map.provider;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
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

import javax.imageio.ImageIO;

import com.ugcs.geohammer.math.GoogleCoordUtils;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import javafx.geometry.Point2D;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TileMapProvider implements MapProvider {

	private static final Logger log = LoggerFactory.getLogger(TileMapProvider.class);

	private static final int MAP_IMAGE_SIZE = 1280;

	private static final int GRID_SIZE = MAP_IMAGE_SIZE / GoogleCoordUtils.TILE_SIZE;

	protected static final int TILE_COUNT = GRID_SIZE * GRID_SIZE;

	protected abstract ExecutorService tileFetchPool();

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

		Tile centralTile = latLonToTile(mapCenter, zoom);
		field.setSceneCenter(getTileCenter(centralTile));

		int m = (GRID_SIZE - 1) / 2;

		record TileFetch(int dx, int dy, CompletableFuture<BufferedImage> future) {}
		List<TileFetch> fetches = new ArrayList<>(TILE_COUNT);
		for (int dx = -m; dx <= m; dx++) {
			for (int dy = -m; dy <= m; dy++) {
				Tile tile = new Tile(centralTile.x() + dx, centralTile.y() + dy, centralTile.z());
				fetches.add(new TileFetch(dx, dy,
						CompletableFuture.supplyAsync(() -> fetchTile(tile), tileFetchPool())));
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
	protected abstract BufferedImage fetchTile(Tile tile);

	protected void writeToCache(BufferedImage image, File target) {
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

	protected record Tile(int x, int y, int z) {}
}

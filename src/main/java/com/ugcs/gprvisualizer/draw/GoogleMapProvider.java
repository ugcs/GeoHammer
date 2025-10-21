package com.ugcs.gprvisualizer.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import org.jspecify.annotations.Nullable;

public class GoogleMapProvider implements MapProvider {

	private static final int MAP_IMAGE_SIZE = 1280;

	@Nullable
	@Override
	public BufferedImage loadimg(MapField field) {

		if (field.getZoom() > getMaxZoom()) {
			field.setZoom(getMaxZoom());
		}

		if (field.getZoom() < getMinZoom()) {
			field.setZoom(getMinZoom());
		}

		LatLon midlPoint = field.getSceneCenter();

		BufferedImage img = null;

		System.setProperty("java.net.useSystemProxies", "true");

		try {
			int intZoom = (int)field.getZoom();
			field.setZoom(intZoom);
			img = createCenteredMapImage(midlPoint, intZoom);
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}

		return img;
	}

	@Override
	public int getMaxZoom() {
		return 20;
	}

	private BufferedImage createCenteredMapImage(LatLon midPoint, int zoom)
			throws IOException, URISyntaxException {

		TileUtils.Tile centralTile = TileUtils.latLonToTile(midPoint, zoom);
		LatLon centralTileMidPoint = TileUtils.tileToLatLoncenter(centralTile);

		// Calculate the pixel offsets from the center of the tile
		int[] centerPixels = TileUtils.pixels(midPoint, zoom);
		int[] centralTileCenterPixels = TileUtils.pixels(centralTileMidPoint, zoom);

		int pixelOffsetX = centerPixels[0] - centralTileCenterPixels[0];
		int pixelOffsetY = centerPixels[1] - centralTileCenterPixels[1];

		// Create a new image with increased size
		BufferedImage combinedImage = new BufferedImage(MAP_IMAGE_SIZE, MAP_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = combinedImage.createGraphics();

		// Calculate the drawing offsets for the tiles
		int drawOffsetX = MAP_IMAGE_SIZE / 2 - TileUtils.TILE_SIZE / 2 - pixelOffsetX;
		int drawOffsetY = MAP_IMAGE_SIZE / 2 - TileUtils.TILE_SIZE / 2 - pixelOffsetY;

		int minX = -2 + (pixelOffsetX < 0 ? -1 : 0);
		int maxX = 2 + (pixelOffsetX > 0 ? 1 : 0);

		int minY = -2 + (pixelOffsetY < 0 ? -1 : 0);
		int maxY = 2 + (pixelOffsetY > 0 ? 1 : 0);

		// Draw the tiles around the central tile
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				BufferedImage tileImage = TileUtils.getTileImage(new TileUtils.Tile(centralTile.x() + x, centralTile.y() + y, centralTile.z()));
				g2d.drawImage(tileImage, drawOffsetX + x * TileUtils.TILE_SIZE, drawOffsetY + y * TileUtils.TILE_SIZE, null);
			}
		}

		g2d.dispose();
		return combinedImage;
	}

	/**
	 * Utility class for working with tiles in the GoogleMapProvider.
	 */
	private static class TileUtils {

		record Tile(int x, int y, int z) {
			Tile(int x, int y, int z) {
				this.x = Math.max(0, x);
				this.y = Math.max(0, y);
				this.z = z;
			}
		}

		private static final int TILE_SIZE = 256;

		public static BufferedImage getTileImage(TileUtils.Tile tile) throws IOException, URISyntaxException {
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
				//System.out.println("Image reads from: " + imagePath);
			} else {
				// If the image doesn't exist, download it and save it to the temporary folder
				tileImage = ImageIO.read(new URI(url).toURL());
				ImageIO.write(tileImage, "png", imageFile);
				//System.out.println("Image downloaded to: " + imagePath);
			}

			return tileImage;
		}
		
		public static int[] pixels(LatLon latLon, int zoom) {
	
			double[] worldCoordinate = project(latLon);
			int scale = 1 << zoom;
			return new int[] { (int) Math.floor(worldCoordinate[0] * scale),
					(int) Math.floor(worldCoordinate[1] * scale) };
		}
	
		public static double[] project(LatLon latLon) {
			var siny = Math.sin((latLon.getLatDgr() * Math.PI) / 180);
	
			// Truncating to 0.9999 effectively limits latitude to 89.189. This is
			// about a third of a tile past the edge of the world tile.
			siny = Math.min(Math.max(siny, -0.9999), 0.9999);
	
			return new double[] {
					TILE_SIZE * (0.5 + latLon.getLonDgr() / 360),
					TILE_SIZE * (0.5 - Math.log((1 + siny) / (1 - siny)) / (4 * Math.PI))
			};
		}
	
		public static Tile latLonToTile(LatLon latLon, int zoom) {
			double lat = latLon.getLatDgr();
			double lon = latLon.getLonDgr();
	
			int x = (int) Math.floor((lon + 180) / 360 * Math.pow(2, zoom));
			int y = (int) Math
					.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2
							* Math.pow(2, zoom));
			return new Tile(x, y, zoom);
		}
	
		public static LatLon tileToLatLonTopLeft(Tile tile) {
			double lon = tile2lon(tile.x(), tile.z());
			double lat = tile2lat(tile.y(), tile.z());
			return new LatLon(lat, lon);
		}
		
		public static LatLon tileToLatLonBottomRight(Tile tile) {
			double lon = tile2lon(tile.x() + 1, tile.z());
			double lat = tile2lat(tile.y() + 1, tile.z());
			return new LatLon(lat, lon);
		}
	
		public static LatLon tileToLatLoncenter(Tile tile) {
			LatLon topLeft = tileToLatLonTopLeft(tile);
			LatLon bottomRight = tileToLatLonBottomRight(tile);
		
			double latCenter = topLeft.getLatDgr() + (bottomRight.getLatDgr() - topLeft.getLatDgr()) / 2;
			double lonCenter = topLeft.getLonDgr() + (bottomRight.getLonDgr() - topLeft.getLonDgr()) / 2;
	
			return new LatLon(latCenter, lonCenter);
		}	
	
		private static double tile2lon(int x, int z) {
			return x / Math.pow(2.0, z) * 360.0 - 180;
		}
	
		private static double tile2lat(int y, int z) {
			double n = Math.PI - 2.0 * Math.PI * y / Math.pow(2.0, z);
			return Math.toDegrees(Math.atan(Math.sinh(n)));
		}
	
	}
}
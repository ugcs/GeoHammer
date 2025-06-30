package com.ugcs.gprvisualizer.draw;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.app.OptionPane;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.GriddingParamsSetted;
import com.ugcs.gprvisualizer.event.WhatChanged;
import edu.mines.jtk.interp.SplinesGridder2;
import javafx.scene.control.Button;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.index.kdtree.KdTree;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.CoordinatesMath;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/**
 * Layer responsible for grid visualization of GPR data.
 * <p>
 * This implementation supports two interpolation methods:
 * 1. Splines interpolation (default):
 * - Uses SplinesGridder2 with high tension (0.9999f)
 * - Suitable for dense, regular data
 * - Works well with small to medium cell sizes
 * <p>
 * 2. IDW (Inverse Distance Weighting):
 * - Better handling of large cell sizes
 * - Prevents artifacts in sparse data areas
 * - Adaptive search radius based on data density
 * - Configurable power parameter for distance weighting
 * <p>
 * The interpolation method can be selected through GriddingParamsSetted event.
 * For large cell sizes or irregular data distribution, IDW is recommended
 * to avoid interpolation artifacts.
 */
@Component
public final class GridLayer extends BaseLayer implements InitializingBean {

	private final Model model;
	private final MapView mapView;
	private final OptionPane optionPane;

	public GridLayer(Model model, MapView mapView, OptionPane optionPane) {
		this.model = model;
		this.mapView = mapView;
		this.optionPane = optionPane;
	}

	private EventHandler<ActionEvent> showMapListener = new EventHandler<ActionEvent>() {
		@Override
		public void handle(ActionEvent event) {
			System.out.println("showMapListener: " + event);
			setActive(optionPane.getGridding().isSelected());
			getRepaintListener().repaint();
		}
	};

	private ThrQueue q;

	private CsvFile currentFile;
	private GriddingParamsSetted currentParams;

	private volatile boolean recalcGrid;
	private boolean toAll;

	@Override
	public void afterPropertiesSet() throws Exception {
		setActive(optionPane.getGridding().isSelected());

		q = new ThrQueue(model, mapView) {
			protected void draw(BufferedImage backImg, MapField field) {
				if (recalcGrid) {
					optionPane.griddingProgress(true);
				}

				Graphics2D g2 = (Graphics2D) backImg.getGraphics();
				g2.translate(backImg.getWidth() / 2, backImg.getHeight() / 2);
				drawOnMapField(g2, field);
			}

			public void ready() {
				optionPane.griddingProgress(false);
				getRepaintListener().repaint();
			}
		};

		optionPane.getGridding().addEventHandler(ActionEvent.ACTION, showMapListener);
	}

	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		if (currentField.getSceneCenter() == null || !isActive()) {
			return;
		}
		q.drawImgOnChangedField(g2, currentField, q.getFront());
	}


	public void drawOnMapField(Graphics2D g2, MapField field) {
		if (isActive()) {
			if (currentFile != null) {
				drawFileOnMapField(g2, field, currentFile);
			}
			setActive(isActive() && optionPane.getGridding().isSelected());
		}
	}

	public static record DataPoint(double latitude, double longitude, double value) implements Comparable<DataPoint> {
		public DataPoint {
			if (latitude < -90 || latitude > 90) {
				throw new IllegalArgumentException("Latitude must be in range [-90, 90]");
			}
			if (longitude < -180 || longitude > 180) {
				throw new IllegalArgumentException("Longitude must be in range [-180, 180]");
			}
		}

		@Override
		public int compareTo(DataPoint o) {
			return latitude == o.latitude ? Double.compare(longitude, o.longitude) : Double.compare(latitude, o.latitude);
		}
	}

	private static double calculateMedian(List<Double> values) {
		Collections.sort(values);
		int size = values.size();
		if (size % 2 == 0) {
			return (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
		} else {
			return values.get(size / 2);
		}
	}

	private static Color getColorForValue(double value, double min, double max) {
		value = Math.clamp(value, min, max);
		double normalized = (value - min) / (max - min);

		javafx.scene.paint.Color color = javafx.scene.paint.Color.hsb((1 - normalized) * 280, 0.8f, 0.8f);
		return new Color((float) color.getRed(), (float) color.getGreen(), (float) color.getBlue(), (float) color.getOpacity());
	}

	/**
	 * Calculates hill-shading illumination value for a given point in the grid.
	 * 
	 * @param gridData The grid data
	 * @param x X coordinate in the grid
	 * @param y Y coordinate in the grid
	 * @param azimuth Light source direction in degrees (0-360, 0=North, 90=East)
	 * @param altitude Light source height in degrees (0-90, 0=horizon, 90=zenith)
	 * @return Illumination value between 0.0 (dark) and 1.0 (bright)
	 */
	private static double calculateHillShading(float[][] gridData, int x, int y, double azimuth, double altitude) {
		// Skip edge cells
		if (x <= 0 || y <= 0 || x >= gridData.length - 1 || y >= gridData[0].length - 1) {
			return 1.0; // Default illumination for edges
		}

		// Check for NaN values in the neighborhood
		if (Float.isNaN(gridData[x-1][y]) || Float.isNaN(gridData[x+1][y]) || 
			Float.isNaN(gridData[x][y-1]) || Float.isNaN(gridData[x][y+1])) {
			return 1.0; // Default illumination if neighbors have NaN
		}

		// Calculate slope components using central difference method
		double dzdx = (gridData[x+1][y] - gridData[x-1][y]) / 2.0;
		double dzdy = (gridData[x][y+1] - gridData[x][y-1]) / 2.0;

		// Calculate slope and aspect
		double slope = Math.atan(Math.sqrt(dzdx*dzdx + dzdy*dzdy));
		double aspect = Math.atan2(dzdy, dzdx);

		// Convert azimuth to radians and adjust to match aspect definition
		double azimuthRad = Math.toRadians(azimuth);

		// Convert altitude to radians
		double altitudeRad = Math.toRadians(altitude);

		// Calculate illumination using the hillshade formula
		double illumination = Math.cos(slope) * Math.sin(altitudeRad) + 
							  Math.sin(slope) * Math.cos(altitudeRad) * 
							  Math.cos(azimuthRad - aspect);

		// Normalize illumination to [0, 1] range
		illumination = Math.max(0.0, illumination);

		return illumination;
	}

	/**
	 * Applies hill-shading effect to a color.
	 * 
	 * @param baseColor The original color
	 * @param illumination Illumination value between 0.0 (dark) and 1.0 (bright)
	 * @param intensity Intensity of the hill-shading effect (0.0-1.0)
	 * @return The shaded color
	 */
	private static Color applyHillShading(Color baseColor, double illumination, double intensity) {
		// Blend between original color and shaded color based on intensity
		float shadeFactor = (float) (1.0 - (1.0 - illumination) * intensity);

		// Convert int RGB values (0-255) to float (0.0-1.0), apply shading, and clamp to valid range
		float r = Math.max(0.0f, Math.min(1.0f, (baseColor.getRed() / 255.0f) * shadeFactor));
		float g = Math.max(0.0f, Math.min(1.0f, (baseColor.getGreen() / 255.0f) * shadeFactor));
		float b = Math.max(0.0f, Math.min(1.0f, (baseColor.getBlue() / 255.0f) * shadeFactor));

		// Keep the original alpha value
		return new Color(r, g, b, baseColor.getAlpha() / 255.0f);
	}

	// Map to store gridding results for each file
	private final Map<File, GriddingResult> griddingResults = new ConcurrentHashMap<>();

	// Class to store gridding result for a file
	private record GriddingResult(
		float[][] gridData, float[][] smoothedGridData,
		LatLon minLatLon, LatLon maxLatLon,
		double cellSize, double blankingDistance,
		Float minValue, Float maxValue,
		String sensor,
		// Hill-shading parameters
		boolean hillShadingEnabled, boolean smoothingEnabled,
		double hillShadingAzimuth,
		double hillShadingAltitude,
		double hillShadingIntensity) {

		public GriddingResult setValues(float minValue, float maxValue, boolean hillShadingEnabled, boolean smoothingEnabled) {
			return new GriddingResult(
					gridData, smoothedGridData,
					minLatLon, maxLatLon,
					cellSize, blankingDistance,
					minValue, maxValue,
					sensor,
					hillShadingEnabled, smoothingEnabled,
					hillShadingAzimuth,
					hillShadingAltitude,
					hillShadingIntensity
			);
		}
	}

	private static float[][] cloneGridData(float[][] gridData) {
		var result = new float[gridData.length][];
		for (int i = 0; i < gridData.length; i++) {
			result[i] = gridData[i].clone();
		}
		return result;
	}

	/**
  * Draws the grid visualization for a given file on the map field.
  * <p>
  * The method performs the following steps:
  * 1. Collects data points from the file
  * 2. Creates a grid based on cell size
  * 3. Interpolates missing values using either:
  * - IDW interpolation (for large cell sizes)
  * - Splines interpolation (for small/medium cell sizes)
  * 4. Applies a low-pass filter to smooth the interpolated data
  * 5. Renders the filtered grid
  * <p>
  * The interpolation method is selected based on GriddingParamsSetted configuration.
  * <p>
  * For the current file, new minValue and maxValue are applied.
  * For other files, stored minValue and maxValue are used to ensure
  * they are displayed without changes from the previous application.
  */
	synchronized private void drawFileOnMapField(Graphics2D g2, MapField field, CsvFile csvFile) {

		var chart = model.getCsvChart(csvFile);
		if (chart.isEmpty()) {
			return;
		}
		String sensor = chart.get().getSelectedSeriesName();

		var savedGriddingRange = optionPane.getSavedGriddingRangeValues(chart.toString() + sensor);
		var minValue = (float) savedGriddingRange.lowValue();
		var maxValue = (float) savedGriddingRange.highValue();

		// Check if we have stored results for this file and if we need to recalculate
		//GriddingResult storedResult = griddingResults.get(csvFile);

	if (recalcGrid) {

			var startFiltering = System.currentTimeMillis();

			List<DataPoint> dataPoints = new ArrayList<>();
			for (CsvFile csvFile1 : model.getFileManager().getCsvFiles().stream().map(f -> (CsvFile) f).filter(f ->
					toAll ? f.isSameTemplate(csvFile) : f.equals(csvFile)).toList()) {
				dataPoints.addAll(getDataPoints(csvFile1, sensor));
			}

			dataPoints = getMedianValues(dataPoints);
			if (dataPoints.isEmpty()) {
				return;
			}

			double minLon = dataPoints.stream().mapToDouble(DataPoint::longitude).min().orElseThrow();
			double maxLon = dataPoints.stream().mapToDouble(DataPoint::longitude).max().orElseThrow();
			double minLat = dataPoints.stream().mapToDouble(DataPoint::latitude).min().orElseThrow();
			double maxLat = dataPoints.stream().mapToDouble(DataPoint::latitude).max().orElseThrow();

			var minLatLon = new LatLon(minLat, minLon);
			var maxLatLon = new LatLon(maxLat, maxLon);

			List<Double> valuesList = new ArrayList<>(dataPoints.stream().map(p -> p.value).toList());
			var median = calculateMedian(valuesList);
			//var average = dataPoints.stream().mapToDouble(p -> p.value).average().getAsDouble();

			int gridSizeX = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(minLat, maxLon)),
					new LatLon(maxLat, minLon).getDistance(new LatLon(maxLat, maxLon)));

			gridSizeX = (int) (gridSizeX / currentParams.getCellSize());

			int gridSizeY = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(maxLat, minLon)),
					new LatLon(minLat, maxLon).getDistance(new LatLon(maxLat, maxLon)));

			gridSizeY = (int) (gridSizeY / currentParams.getCellSize());

			double lonStep = (maxLon - minLon) / gridSizeX;
			double latStep = (maxLat - minLat) / gridSizeY;

			var gridData = new float[gridSizeX][gridSizeY];

			boolean[][] m = new boolean[gridSizeX][gridSizeY];
			for (int i = 0; i < gridSizeX; i++) {
				for (int j = 0; j < gridSizeY; j++) {
					m[i][j] = true;
				}
			}

			Map<String, List<Double>> points = new HashMap<>();
			for (DataPoint point : dataPoints) {
				int xIndex = (int) ((point.longitude - minLon) / lonStep);
				int yIndex = (int) ((point.latitude - minLat) / latStep);

				String key = xIndex + "," + yIndex;
				points.computeIfAbsent(key, (k -> new ArrayList<Double>())).add(point.value);
			}

			var gridBDx = gridSizeX / (gridSizeX * currentParams.getCellSize() / currentParams.getBlankingDistance());
			var gridBDy = gridSizeY / (gridSizeY * currentParams.getCellSize() / currentParams.getBlankingDistance());
			var visiblePoints = new boolean[gridSizeX][gridSizeY];

			for (Map.Entry<String, List<Double>> entry : points.entrySet()) {
				String[] coords = entry.getKey().split(",");
				int xIndex = Integer.parseInt(coords[0]);
				int yIndex = Integer.parseInt(coords[1]);
				double medianValue = calculateMedian(entry.getValue());
				try {
					gridData[xIndex][yIndex] = (float) medianValue;
					m[xIndex][yIndex] = false;

					for (int dx = -(int) gridBDx; dx <= gridBDx; dx++) {
						for (int dy = -(int) gridBDy; dy <= gridBDy; dy++) {
							int nx = xIndex + dx;
							int ny = yIndex + dy;
							if (nx >= 0 && nx < gridSizeX && ny >= 0 && ny < gridSizeY) {
								visiblePoints[nx][ny] = true;
							}
						}
					}


				} catch (ArrayIndexOutOfBoundsException e) {
					System.out.println("Out of bounds - xIndex = " + xIndex + " yIndex = " + yIndex);
				}
			}

			//double lonStepBD = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / (gridSizeX * cellSize / blankingDistance);
			//double latStepBD = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / (gridSizeY * cellSize / blankingDistance);

			// Build a minimal polygon containing all data points and check current point.
			//var geomFactory = new org.locationtech.jts.geom.GeometryFactory();
			//var hullPolygon = geomFactory.createPolygon(new org.locationtech.jts.algorithm.ConvexHull(
			//		dataPoints.stream()
			//				.map(p -> new Coordinate(p.longitude, p.latitude))
			//				.toArray(Coordinate[]::new), new GeometryFactory())
			//		.getConvexHull()
			//		.getCoordinates());


			int count = 0;

			m = thinOutBooleanGrid(m);

			for (int i = 0; i < gridData.length; i++) {
				for (int j = 0; j < gridData[0].length; j++) {

					//if (gridData[i][j] != 0) {
					//	continue;
					//}

					if (!m[i][j]) {
						continue;
					}

					//double lat = minLatLon.getLatDgr() + j * latStep;
					//double lon = minLatLon.getLonDgr() + i * lonStep;

					//TODO: convert to use nearest neighbors
					//if (!isInBlankingDistanceToKnownPoints(lat, lon, dataPoints, blankingDistance)) {
					//	gridData[i][j] = Float.NaN;
					//	continue;
					//}

					//var delta = 1;
					//List<KdNode> neighbors = kdTree.query(new Envelope(lon - delta*lonStepBD, lon + delta*lonStepBD, lat - delta * latStepBD, lat + delta * latStepBD)); // maxNeighbors);

					//gridData[i][j] = (float) average;//Float.NaN;

					//if (neighbors.isEmpty()) {
					//gridData[i][j] = (float) average;//Float.NaN;
					//	m[i][j] = false;
					//	count++;
					//}

					gridData[i][j] = (float) median;
					//if (!hullPolygon.contains(geomFactory.createPoint(new Coordinate(lon, lat)))) {
					//	m[i][j] = false;
					//}

					if (!visiblePoints[i][j]) {
						m[i][j] = false;
						count++;
					}
				}
			}

			System.out.println("Filtering complete in " + (System.currentTimeMillis() - startFiltering) / 1000 + "s");
			System.out.println("Aditional points: " + count);


				System.out.println("Splines interpolation");
				var start = System.currentTimeMillis();
				// Use original splines interpolation
				var gridder = new SplinesGridder2();
				var maxIterations = 100;
				var tension = 0f;

				gridder.setMaxIterations(maxIterations); // 200 if the anomaly
				gridder.setTension(tension); //0.9999999f); - maximum
				gridder.gridMissing(m, gridData);
				if (gridder.getIterationCount() >= maxIterations) {
					tension = 0.999999f;
					maxIterations = 200;
					gridder.setTension(tension);
					gridder.setMaxIterations(maxIterations);
					gridder.gridMissing(m, gridData);
				}
 			System.out.println("Iterations: " + gridder.getIterationCount() + " time: " + (System.currentTimeMillis() - start) / 1000 + "s" + " tension: " + tension + " maxIterations: " + maxIterations);

			System.out.println("Interpolation complete");

			for (int i = 0; i < gridData.length; i++) {
				for (int j = 0; j < gridData[0].length; j++) {

					//double lat = minLatLon.getLatDgr() + j * latStep;
					//double lon = minLatLon.getLonDgr() + i * lonStep;

					//if (!hullPolygon.contains(geomFactory.createPoint(new Coordinate(lon, lat)))) {
					//	gridData[i][j] = Float.NaN;
					//	continue;
					//}

					if (!visiblePoints[i][j]) {
						gridData[i][j] = Float.NaN;
					}

					//if (!hullPolygon.contains(geomFactory.createPoint(new Coordinate(lon, lat)))) {
					//	gridData[i][j] = Float.NaN;
					//} else {
					//	var delta = 1;
					//List<KdNode> neighbors = kdTree.query(new Envelope(lon - delta*lonStepBD, lon + delta*lonStepBD, lat - delta * latStepBD, lat + delta * latStepBD)); // maxNeighbors);
					//if (neighbors.isEmpty()) {
					//	gridData[i][j] = Float.NaN;
					//count++;
					//}
					//}
				}
			}

			//removeFromGriddingResults(csvFile);
			// Store the gridding result for this file
			griddingResults.put(csvFile.getFile(), new GriddingResult(
					gridData, // Deep cloning is done in the constructor
					applyLowPassFilter(gridData),
					minLatLon,
					maxLatLon,
					currentParams.getCellSize(),
					currentParams.getBlankingDistance(),
					minValue,
					maxValue,
					sensor,
					currentParams.isHillShadingEnabled(),
					currentParams.isSmoothingEnabled(),
					currentParams.getHillShadingAzimuth(),
					currentParams.getHillShadingAltitude(),
					currentParams.getHillShadingIntensity()
			));

			recalcGrid = false;
		}

		if (griddingResults.get(csvFile.getFile()) instanceof GriddingResult gr && gr.sensor.equals(sensor)) {
			griddingResults.put(csvFile.getFile(), gr.setValues(minValue,maxValue,
					currentParams.isHillShadingEnabled(),
					currentParams.isSmoothingEnabled()));
		}

		var gr = griddingResults.remove(csvFile.getFile());
		for (var griddingResult : griddingResults.values()) {
			print(g2, field, griddingResult);
		}

		if (gr != null) {
			print(g2, field, gr);
			griddingResults.put(csvFile.getFile(), gr);
		}
	}


	/**
	 * Before thinning, determine the minimum number of true values per row and column.
	 */
	private static int[] computeRowColMin(boolean[][] gridData) {
		int rows = gridData.length;
		int cols = rows > 0 ? gridData[0].length : 0;
		int minRowTrue = Integer.MAX_VALUE;
		int[] rowCounts = new int[rows];
		int[] colCounts = new int[cols];

		for (int i = 0; i < rows; i++) {
			int countRow = 0;
			for (int j = 0; j < cols; j++) {
				if (!gridData[i][j]) {
					countRow++;
					colCounts[j]++;
				}
			}
			rowCounts[i] = countRow;
		}
		int rowsum = 0;
		int rowcount = 0;
		for (int i = 0; i < rows; i++) {
			if (rowCounts[i] > cols * 0.01) {
				rowsum += rowCounts[i];
				rowcount++;
			}
		}

		int colsum = 0;
		int colcount = 0;
		for (int j = 0; j < cols; j++) {
			if (colCounts[j] > rows * 0.01) {
				colsum += colCounts[j];
				colcount++;
			}
		}
		return new int[]{rowsum / (rowcount != 0 ? rowcount : 1), colsum / (colcount != 0 ? colcount : 1)};
	}

	/**
	 * Applies a low-pass filter to the grid data to smooth out high-frequency variations.
	 * Uses a Gaussian kernel for the convolution.
	 * 
	 * @param gridData The grid data to filter
	 */
	private float[][] applyLowPassFilter(float[][] gridData) {
		if (gridData == null || gridData.length == 0 || gridData[0].length == 0) {
			return gridData;
		}

		int kernelSize = 15;
		int kernelRadius = 7;

		System.out.println("Applying low-pass filter with " + kernelSize + "x" + kernelSize + " kernel");
		long startTime = System.currentTimeMillis();

		float[][] kernel = new float[kernelSize][kernelSize];

		// Initialize kernel with Gaussian values
		double sigma = 5.0;
		double sum = 0.0;

		for (int x = -kernelRadius; x <= kernelRadius; x++) {
			for (int y = -kernelRadius; y <= kernelRadius; y++) {
				double value = Math.exp(-(x*x + y*y) / (2 * sigma * sigma));
				kernel[x+ kernelRadius][y+ kernelRadius] = (float)value;
				sum += value;
			}
		}

		// Normalize kernel
		for (int i = 0; i < kernelSize; i++) {
			for (int j = 0; j < kernelSize; j++) {
				kernel[i][j] /= sum;
			}
		}

		int width = gridData.length;
		int height = gridData[0].length;

		var resultData = cloneGridData(gridData);

		// Apply convolution
		for (int i = kernelRadius; i < width - kernelRadius; i++) {
			for (int j = kernelRadius; j < height - kernelRadius; j++) {
				// Skip NaN values
				if (Float.isNaN(gridData[i][j])) {
					continue;
				}

				float sum2 = 0;
				float weightSum = 0;

				// Apply kernel
				for (int ki = -kernelRadius; ki <= kernelRadius; ki++) {
					for (int kj = -kernelRadius; kj <= kernelRadius; kj++) {
						int ni = i + ki;
						int nj = j + kj;

						// Skip out of bounds or NaN values
						if (ni < 0 || ni >= width || nj < 0 || nj >= height || Float.isNaN(gridData[ni][nj])) {
							continue;
						}

						float weight = kernel[ki+ kernelRadius][kj+ kernelRadius];
						sum2 += gridData[ni][nj] * weight;
						weightSum += weight;
					}
				}

				// Normalize by the sum of weights
				if (weightSum > 0) {
					resultData[i][j] = sum2 / weightSum;
				}
			}
		}

		System.out.println("Low-pass filter applied in " + (System.currentTimeMillis() - startTime) + "ms");
		return resultData;
	}

	/**
	 * Thin out the matrix by rows and columns so that the minimum density is not reduced.
	 * If almost all cells are filled, the array is returned unchanged.
	 */
	public static boolean[][] thinOutBooleanGrid(boolean[][] gridData) {
		int rows = gridData.length;
		int cols = rows > 0 ? gridData[0].length : 0;

		int[] minValues = computeRowColMin(gridData);
		int minRowTrue = minValues[0];
		int minColTrue = minValues[1];

		if (minRowTrue >= cols * 0.9 && minColTrue >= rows * 0.9 || minRowTrue == 0 && minColTrue == 0) {
			return gridData;
		}

		double avg = Math.min(0.22, Math.min((double) minRowTrue / cols, (double) minColTrue / rows));

		if (avg < 0.05) {
			return gridData;
		}

		boolean[][] result = new boolean[rows][cols];
		for (int i = 0; i < rows; i++) {
			System.arraycopy(gridData[i], 0, result[i], 0, cols);
		}

		for (int i = 0; i < rows; i++) {
			List<Integer> trueIndices = new ArrayList<>();
			for (int j = 0; j < cols; j++) {
				if (!result[i][j]) {
					trueIndices.add(j);
				}
			}
			int count = trueIndices.size();
			minRowTrue = (int) (avg * cols);
			if (count > minRowTrue && minRowTrue > 0) {
				List<Integer> keepIndices = new ArrayList<>();
				double step = (double) (count - 1) / (minRowTrue - 1);
				for (int k = 0; k < minRowTrue; k++) {
					int index = trueIndices.get((int) Math.round(k * step));
					keepIndices.add(index);
				}
				for (int j = 0; j < cols; j++) {
					result[i][j] = true;
				}
				for (int j : keepIndices) {
					result[i][j] = false;
				}
			}
		}

		for (int j = 0; j < cols; j++) {
			List<Integer> trueIndices = new ArrayList<>();
			for (int i = 0; i < rows; i++) {
				if (!result[i][j]) {
					trueIndices.add(i);
				}
			}
			int count = trueIndices.size();
			minColTrue = (int) (avg * rows);
			if (count > minColTrue && minColTrue > 0) {
				List<Integer> keepIndices = new ArrayList<>();
				double step = (double) (count - 1) / (minColTrue - 1);
				for (int k = 0; k < minColTrue; k++) {
					int index = trueIndices.get((int) Math.round(k * step));
					keepIndices.add(index);
				}
				for (int i = 0; i < rows; i++) {
					result[i][j] = true;
				}
				for (int i : keepIndices) {
					result[i][j] = false;
				}
			}
		}
		return result;
	}

	private void print(Graphics2D g2, MapField field, GriddingResult gr) {

		Float minValue = gr.minValue;
		Float maxValue = gr.maxValue;
		System.out.println("Printing minValue = " + minValue + " maxValue = " + maxValue);

		var gridData = gr.smoothingEnabled ? gr.smoothedGridData
				: gr.gridData;

		var minLatLon = gr.minLatLon;
		var maxLatLon = gr.maxLatLon;

		int gridSizeX = gridData.length;
		int gridSizeY = gridData[0].length;

		double lonStep = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / gridSizeX;
		double latStep = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / gridSizeY;

		var minLatLonPoint = field.latLonToScreen(minLatLon);
		var nextLatLonPoint = field.latLonToScreen(new LatLon(minLatLon.getLatDgr() + latStep, minLatLon.getLonDgr() + lonStep));

		double cellWidth = Math.abs(minLatLonPoint.getX() - nextLatLonPoint.getX()) + 1; //3; //width / gridSizeX;
		double cellHeight = Math.abs(minLatLonPoint.getY() - nextLatLonPoint.getY()) + 1; //3; //height / gridSizeY;

		System.out.println("cellWidth = " + cellWidth + " cellHeight = " + cellHeight);

		// Log hill-shading parameters if enabled
		if (gr.hillShadingEnabled) {
			System.out.println("Hill-shading enabled with azimuth=" + gr.hillShadingAzimuth + 
				", altitude=" + gr.hillShadingAltitude + ", intensity=" + gr.hillShadingIntensity);
		}

		for (int i = 0; i < gridData.length; i++) {
			for (int j = 0; j < gridData[0].length; j++) {
				try {
					var value = gridData[i][j];
					if (Double.isNaN(value) || Float.isNaN(value)) {
						continue;
					}

					// Get base color for the value
					Color color = getColorForValue(value, minValue, maxValue);

					// Apply hill-shading if enabled
					if (gr.hillShadingEnabled) {
						// Calculate illumination for this cell
						double illumination = calculateHillShading(
							gridData, i, j, 
							gr.hillShadingAzimuth, 
							gr.hillShadingAltitude
						);

						// Apply hill-shading to the color
						color = applyHillShading(color, illumination, gr.hillShadingIntensity);
					}

					g2.setColor(color);

					double lat = minLatLon.getLatDgr() + j * latStep;
					double lon = minLatLon.getLonDgr() + i * lonStep;

					var point = field.latLonToScreen(new LatLon(lat, lon));
					g2.fillRect((int) point.getX(), (int) point.getY(), (int) cellWidth, (int) cellHeight);
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
		}
	}

	private List<DataPoint> getDataPoints(CsvFile csvFile, String sensor) {
		return csvFile.getGeoData().stream().filter(gd -> gd.getSensorValue(sensor).data() != null)
				.map(gd -> new DataPoint(gd.getLatitude(), gd.getLongitude(), gd.getSensorValue(sensor).data().doubleValue())).toList();
	}

	private static KdTree buildKdTree(List<DataPoint> dataPoints) {
		KdTree kdTree = new KdTree(0.0000);
		for (DataPoint point : dataPoints) {
			Coordinate coord = new Coordinate(point.longitude, point.latitude);
			kdTree.insert(coord, point);
		}
		return kdTree;
	}

	private static boolean isInBlankingDistanceToKnownPoints(double lat, double lon, List<DataPoint> knownPoints, double blankingDistance) {
		for (DataPoint point : knownPoints) {
			double distance = CoordinatesMath.measure(lat, lon, point.latitude, point.longitude);
			if (distance <= blankingDistance) {
				return true;
			}
		}
		return false;
	}

	private static List<DataPoint> getMedianValues(List<DataPoint> dataPoints) {
		Map<String, List<Double>> dataMap = new HashMap<>();
		for (DataPoint point : dataPoints) {
			String key = point.latitude + "," + point.longitude;
			dataMap.computeIfAbsent(key, k -> new ArrayList<>()).add(point.value);
		}

		List<DataPoint> medianDataPoints = new ArrayList<>();
		for (Map.Entry<String, List<Double>> entry : dataMap.entrySet()) {
			String[] coords = entry.getKey().split(",");
			double latitude = Double.parseDouble(coords[0]);
			double longitude = Double.parseDouble(coords[1]);
			//double averageValue = entry.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
			double medianValue = calculateMedian(entry.getValue());
			//System.out.println("latitude = " + latitude + " longitude = " + longitude + " averageValue = " + averageValue + " calculateValue = " + calculateValue);
			medianDataPoints.add(new DataPoint(latitude, longitude, medianValue));
		}

		return medianDataPoints;
	}

	@EventListener
	public void handleFileSelectedEvent(FileSelectedEvent event) {
		this.currentFile = event.getFile() instanceof CsvFile csvFile ? csvFile : null;
		//toAll = false;
		// Don't recalculate grid when file is selected, use stored results if available
		//recalcGrid = false;

		// Only clear the grid (setActive(false)) when a file is closed (this.file is null)
		// This ensures the grid is not cleared when switching between files
		//if (this.file == null) {
		//	setActive(false);
		//}

		if (this.currentFile != null) {
			q.add();
		}
	}

	@EventListener
	private void handleFileClosedEvent(FileClosedEvent event) {
		if (event.getSgyFile() instanceof CsvFile csvFile) {
			System.out.println(this + " catched that file has been closed: " + event.getSgyFile() + ", source: " + event.getSource());
			int before = griddingResults.size();
			removeFromGriddingResults(csvFile);
			if (before <= griddingResults.size()) {
				System.err.println("was not deleted: " + griddingResults);
			}
			if (griddingResults.size() == 0) {
				q.add();
			}
		}

			//var gr = griddingResults.remove(csvFile);
			//if (gr != null) {
			//	griddingResults.entrySet().stream().filter(e -> e.getValue()
			//					.equals(gr))
			//			.map(e -> e.getKey())
			//			.forEach(k -> griddingResults.remove(k));
			//	q.add();
			//}
		//}
	}
	
	/**
	 * Handles file rename events to invalidate cached gridding results.
	 * <p>
	 * When a file is renamed, the cached gridding results for that file
	 * need to be invalidated to ensure that the grid is recomputed with
	 * the new file path. This method removes the old gridding results
	 * from the cache and triggers a grid recalculation.
	 *
	 * @param event the file rename event
	 */
	@EventListener
	private void handleFileRenameEvent(com.ugcs.gprvisualizer.event.FileRenameEvent event) {
		if (event.getSgyFile() instanceof CsvFile csvFile) {
			System.out.println(this + " detected file rename: " + event.getOldFile() + " -> " + csvFile.getFile());
			int before = griddingResults.size();
			// Remove the old gridding results for this file
			//removeFromGriddingResults(csvFile);
			switchGriddingResult(csvFile, event.getOldFile());
			if (before < griddingResults.size()) {
				System.err.println("was not changed: " + griddingResults);
			}
			// Force a grid recalculation
			//if (csvFile.equals(currentFile)) {
			//	recalcGrid = true;
			//	q.add();
			//}
		}
	}

	private void switchGriddingResult(CsvFile csvFile, File oldFile) {
		//var copy = csvFile.copy();
		//var newFile = csvFile.getFile();
		//csvFile.setFile(oldFile);
		if (griddingResults.remove(oldFile) instanceof GriddingResult gr) {
			//csvFile.setFile(newFile);
			griddingResults.put(csvFile.getFile(), gr);
		}
	}

	private void removeFromGriddingResults(CsvFile csvFile) {
		System.out.println("griddingResultsCount: " + griddingResults.size() + ", griddingResults: " + griddingResults);
		griddingResults.remove(csvFile.getFile());
		System.out.println("griddingResultsCount: " + griddingResults.size() + ", griddingResults: " + griddingResults);
		if (model.getFileManager().getCsvFiles().isEmpty() && griddingResults.size() > 0) {
			System.err.println("Have to clear griddingResultsCount: " + griddingResults.size() + ", griddingResults: " + griddingResults);
			griddingResults.clear();
		}
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isZoom() || changed.isWindowresized()
				|| changed.isAdjusting()
				|| changed.isMapscroll()
				//|| changed.isJustdraw()
				|| changed.isGriddingRangeChanged()) {
			if (isActive()) {
				System.out.println("GridLayer: " + changed + ", griddingResults: " + griddingResults);
				if (!recalcGrid) {
					q.add();
				} else {
					//setActive(false);
				}
			}
		} else if (changed.isCsvDataFiltered()) {
			recalcGrid = true;
			q.add();
		}
	}

	/**
	 * Handles grid parameter updates from the UI.
	 * <p>
	 * Updates include:
	 * - Cell size for grid resolution
	 * - Blanking distance for data filtering
	 * - Interpolation method selection
	 * - IDW-specific parameters (when IDW is selected)
	 * <p>
	 * Triggers grid recalculation only when explicitly requested through the UI.
	 */
	@EventListener(GriddingParamsSetted.class)
	private void gridParamsSetted(GriddingParamsSetted griddingParamsSetted) {
		currentParams = griddingParamsSetted;
		toAll = griddingParamsSetted.isToAll();

		// Only recalculate grid when explicitly requested through the UI
		// This is triggered by the "Apply" or "Apply to all" buttons
		if (griddingParamsSetted.getSource() instanceof Button) {
			recalcGrid = true;
		}

		setActive(true);
		q.add();
	}
}

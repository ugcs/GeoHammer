package com.ugcs.gprvisualizer.draw;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.app.OptionPane;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.GriddingParamsSetted;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.math.IDWInterpolator;
import edu.mines.jtk.interp.SplinesGridder2;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.index.kdtree.KdNode;
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
 * 
 * This implementation supports two interpolation methods:
 * 1. Splines interpolation (default):
 *    - Uses SplinesGridder2 with high tension (0.9999f)
 *    - Suitable for dense, regular data
 *    - Works well with small to medium cell sizes
 * 
 * 2. IDW (Inverse Distance Weighting):
 *    - Better handling of large cell sizes
 *    - Prevents artifacts in sparse data areas
 *    - Adaptive search radius based on data density
 *    - Configurable power parameter for distance weighting
 * 
 * The interpolation method can be selected through GriddingParamsSetted event.
 * For large cell sizes or irregular data distribution, IDW is recommended
 * to avoid interpolation artifacts.
 */
@Component
public class GridLayer extends BaseLayer implements InitializingBean {

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

	private CsvFile file;
	private double cellSize;
	private double blankingDistance;
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
			if (recalcGrid) {
				//setActive(false);
				//getRepaintListener().repaint();
			}

			if (file != null && cellSize != 0 && blankingDistance != 0) {
				drawFileOnMapField(g2, field, file);
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
		public int compareTo(@NotNull DataPoint o) {
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

	private List<DataPoint> dataPoints;
	private LatLon minLatLon;
	private LatLon maxLatLon;
	private float[][] gridData;

	/**
	 * Draws the grid visualization for a given file on the map field.
	 * 
	 * The method performs the following steps:
	 * 1. Collects data points from the file
	 * 2. Creates a grid based on cell size
	 * 3. Interpolates missing values using either:
	 *    - IDW interpolation (for large cell sizes)
	 *    - Splines interpolation (for small/medium cell sizes)
	 * 4. Renders the interpolated grid
	 * 
	 * The interpolation method is selected based on GriddingParamsSetted configuration.
	 */
	private void drawFileOnMapField(Graphics2D g2, MapField field, CsvFile csvFile) {

		var minValue = optionPane.getGriddingRangeSlider().isDisabled() ? null : (float) optionPane.getGriddingRangeSlider().getLowValue();
		var maxValue = optionPane.getGriddingRangeSlider().isDisabled() ? null : (float) optionPane.getGriddingRangeSlider().getHighValue();

		if (recalcGrid) {
			var chart = model.getChart(csvFile);
			String sensor = chart.get().getSelectedSeriesName();

			var startFiltering = System.currentTimeMillis();

			dataPoints = new ArrayList<>();
			for(CsvFile csvFile1: model.getFileManager().getCsvFiles().stream().map(f -> (CsvFile)f).filter(f ->
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
			minLatLon = new LatLon(minLat, minLon);
			maxLatLon = new LatLon(maxLat, maxLon);

			List<Double> valuesList = new ArrayList<>(dataPoints.stream().map(p -> p.value).toList());
			var median = calculateMedian(valuesList);
			//var average = dataPoints.stream().mapToDouble(p -> p.value).average().getAsDouble();

			int gridSizeX = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(minLat, maxLon)),
					new LatLon(maxLat, minLon).getDistance(new LatLon(maxLat, maxLon)));

			gridSizeX = (int) (gridSizeX / cellSize);

			int gridSizeY = (int) Math.max(new LatLon(minLat, minLon).getDistance(new LatLon(maxLat, minLon)),
					new LatLon(minLat, maxLon).getDistance(new LatLon(maxLat, maxLon)));

			gridSizeY = (int) (gridSizeY / cellSize);

			double lonStep = (maxLon - minLon) / gridSizeX;
			double latStep = (maxLat - minLat) / gridSizeY;

			gridData = new float[gridSizeX][gridSizeY];

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

			var gridBDx = gridSizeX / (gridSizeX * cellSize / blankingDistance);
			var gridBDy = gridSizeY / (gridSizeY * cellSize / blankingDistance);
			var visiblePoints = new boolean[gridSizeX][gridSizeY];

			for (Map.Entry<String, List<Double>> entry : points.entrySet()) {
				String[] coords = entry.getKey().split(",");
				int xIndex = Integer.parseInt(coords[0]);
				int yIndex = Integer.parseInt(coords[1]);
				double medianValue = calculateMedian(entry.getValue());
				try {
					gridData[xIndex][yIndex] = (float) medianValue;
					m[xIndex][yIndex] = false;

					for (int dx = -(int)gridBDx; dx <= gridBDx; dx++) {
						for (int dy = -(int)gridBDy; dy <= gridBDy; dy++) {
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

			if (1 != 1 && currentParams != null && currentParams.getInterpolationMethod() == GriddingParamsSetted.InterpolationMethod.IDW) {
				System.out.println("IDW interpolation");

				Collections.shuffle(dataPoints);
				KdTree kdTree = buildKdTree(dataPoints);
				System.out.println("kdTree depth: " + kdTree.depth());

				// Initialize IDW interpolator with configured parameters
				var idwInterpolator = new IDWInterpolator(
					kdTree,
					currentParams.getIdwPower(),
					currentParams.getIdwMinPoints(),
					cellSize * 2, // max search radius
					cellSize     // initial search radius
				);

				// Interpolate missing values using IDW
				for (int i = 0; i < gridData.length; i++) {
					for (int j = 0; j < gridData[0].length; j++) {
						if (m[i][j]) { // if point is missing
							double lon = minLatLon.getLonDgr() + i * lonStep;
							double lat = minLatLon.getLatDgr() + j * latStep;
							double interpolatedValue = idwInterpolator.interpolate(lon, lat);
							gridData[i][j] = (float) interpolatedValue;
						}
					}
				}
			} else {
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
			}

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

			recalcGrid = false;
		}

		if (minValue == null || maxValue == null) {
			return;
		}

		System.out.println("Printing minValue = " + minValue + " maxValue = " + maxValue);
		print(g2, field, minValue, maxValue);
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
					int index = trueIndices.get((int)Math.round(k * step));
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
					int index = trueIndices.get((int)Math.round(k * step));
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

	private void print(Graphics2D g2, MapField field, Float minValue, Float maxValue) {
		int gridSizeX = gridData.length;
		int gridSizeY = gridData[0].length;

		double lonStep = (maxLatLon.getLonDgr() - minLatLon.getLonDgr()) / gridSizeX;
		double latStep = (maxLatLon.getLatDgr() - minLatLon.getLatDgr()) / gridSizeY;

		var minLatLonPoint = field.latLonToScreen(minLatLon);
		var nextLatLonPoint = field.latLonToScreen(new LatLon(minLatLon.getLatDgr() + latStep, minLatLon.getLonDgr() + lonStep));

		double cellWidth = Math.abs(minLatLonPoint.getX() - nextLatLonPoint.getX()) + 1; //3; //width / gridSizeX;
		double cellHeight = Math.abs(minLatLonPoint.getY() - nextLatLonPoint.getY()) + 1; //3; //height / gridSizeY;

		System.out.println("cellWidth = " + cellWidth + " cellHeight = " + cellHeight);

		for (int i = 0; i < gridData.length; i++) {
			for (int j = 0; j < gridData[0].length; j++) {
				try {
					var value = gridData[i][j];
					if (Double.isNaN(value) || Float.isNaN(value)) {
						continue;
					}

					Color color = getColorForValue(value, minValue, maxValue);
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
		this.file = event.getFile() instanceof CsvFile ? (CsvFile) event.getFile() : null;
		toAll = false;
		recalcGrid = true;
		//setActive(false);
		//q.add();
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isZoom() || changed.isWindowresized()
				|| changed.isAdjusting()
				|| changed.isMapscroll()
				|| changed.isJustdraw()) {
			//if (isActive()) {
				//q.add();
			//}
		} else if (changed.isGriddingRangeChanged()) {
			if (isActive()) {
				q.add();
			}
		} else if (changed.isCsvDataFiltered()) {
			recalcGrid = true;
			q.add();
		} else if (changed.isTraceCut()) {
			recalcGrid = true;
			setActive(false);
		}
	}

	/**
	 * Handles grid parameter updates from the UI.
	 * 
	 * Updates include:
	 * - Cell size for grid resolution
	 * - Blanking distance for data filtering
	 * - Interpolation method selection
	 * - IDW-specific parameters (when IDW is selected)
	 * 
	 * Triggers grid recalculation when parameters change.
	 */
	@EventListener(GriddingParamsSetted.class)
	private void gridParamsSetted(GriddingParamsSetted griddingParamsSetted) {
		currentParams = griddingParamsSetted;
		cellSize = griddingParamsSetted.getCellSize();
		blankingDistance = griddingParamsSetted.getBlankingDistance();
		toAll = griddingParamsSetted.isToAll();
		recalcGrid = true;
		setActive(true);
		q.add();
	}
}

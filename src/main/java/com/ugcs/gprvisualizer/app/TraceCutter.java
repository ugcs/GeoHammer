package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileNames;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationEventPublisher;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceCutInitializer;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.draw.Layer;
import com.ugcs.gprvisualizer.draw.RepaintListener;
import com.ugcs.gprvisualizer.dzt.DztFile;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

@Component
public class TraceCutter implements Layer, InitializingBean {

	private static final int RADIUS = 5;
	
	private MapField field;
	private List<LatLon> points;	
	private Integer active = null;

	Map<Integer, Boolean> activePoints = new HashMap<>();
	
	private final Model model;
	private final ApplicationEventPublisher eventPublisher;
	
	private RepaintListener listener;
	
	private ToggleButton buttonCutMode = ResourceImageHolder.setButtonImage(ResourceImageHolder.SELECT_RECT, new ToggleButton());
	private Button buttonCrop = ResourceImageHolder.setButtonImage(ResourceImageHolder.CROP, new Button());
	private Button buttonSplit = ResourceImageHolder.setButtonImage(ResourceImageHolder.SPLIT, new Button());
	private Button buttonUndo = ResourceImageHolder.setButtonImage(ResourceImageHolder.UNDO, new Button());

	private final List<SgyFile> undoFiles = new ArrayList<>();
	
	{
		buttonCutMode.setTooltip(new Tooltip("Select area"));
		buttonUndo.setTooltip(new Tooltip("Undo"));
		buttonCrop.setTooltip(new Tooltip("Apply crop"));
		buttonSplit.setTooltip(new Tooltip("Split the line"));
	}

	public TraceCutter(Model model, ApplicationEventPublisher eventPublisher) {
		this.model = model;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.field = model.getMapField();
	}

	public void clear() {
		points = null;
		active = null;
		activePoints.clear();
	}

	public void init() {		
		points = new TraceCutInitializer()
			.initialRect(model, model.getTraces());
		activePoints.clear();
	}
	
	@Override
	public boolean mousePressed(Point2D point) {
		if (points == null) {
			return false;
		}
		
		List<Point2D> border = getScreenPoligon(field);
		for (int i = 0; i < border.size(); i++) {
			Point2D p = border.get(i);
			if (point.distance(p) < RADIUS) {
				active = i;
				getListener().repaint();
				return true;
			}			
		}
		active = null;
		return false;
	}
	
	@Override
	public boolean mouseRelease(Point2D point) {
		if (points == null) {
			return false;
		}
		
		if (active != null) {
			getListener().repaint();
			active = null;
		
			return true;
		}
		return false;
	}
	
	@Override
	public boolean mouseMove(Point2D point) {
		if (points == null) {
			return false;
		}
		
		if (active == null) {
			return false;
		}
		
		points.get(active).from(field.screenTolatLon(point));
		if (active % 2 == 0) {
			if (!isActive(active + 1)) {
				points.get(active + 1).from(TraceCutInitializer.getMiddleOf((active + 2) < points.size() ? points.get(active + 2) : points.get(0), field.screenTolatLon(point)));
			}
			if (!isActive(active == 0 ? points.size() - 1 : active - 1)) {
				(active == 0 ? points.get(points.size() - 1) : points.get(active - 1))
				.from(TraceCutInitializer.getMiddleOf(active == 0 ? points.get(points.size() - 2) : points.get(active - 2), field.screenTolatLon(point)));
			}
		} else {
			activePoints.put(active, !isInTheMiddle(active));
		}

		getListener().repaint();		
		return true;
	}
	
	private boolean isActive(int pointIndex) {
		return activePoints.computeIfAbsent(pointIndex, i -> false);
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		if (points == null) {
			return;
		}
		
		List<Point2D> border = getScreenPoligon(field);
		
		for (int i = 0; i < border.size(); i++) {
			
			Point2D p1 = border.get(i);
			Point2D p2 = border.get((i + 1) % border.size());
			
			g2.setColor(Color.YELLOW);
			g2.drawLine((int) p1.getX(), (int) p1.getY(), 
					(int) p2.getX(), (int) p2.getY());
		}
		
		for (int i = 0; i < border.size(); i++) {
			Point2D p1 = border.get(i);			
			
			if ((i+1) % 2 == 0 && !isActive(i)) {
				g2.setColor(Color.GRAY);
			} else {
				g2.setColor(Color.WHITE);
			}
			 
			g2.fillOval((int) p1.getX() - RADIUS, 
					(int) p1.getY() - RADIUS,
					2 * RADIUS, 2 * RADIUS);
			if (active != null && active == i) {
				g2.setColor(Color.BLUE);
				g2.drawOval((int) p1.getX() - RADIUS,
						(int) p1.getY() - RADIUS,
						2 * RADIUS, 2 * RADIUS);
			}			
		}		
	}

	private boolean isInTheMiddle(int pointIndex) {
		List<Point2D> border = getScreenPoligon(field);
		return isInTheMiddle(
			border.get(pointIndex - 1), 
			border.get(pointIndex), 
			(pointIndex + 1 < border.size()) ? border.get(pointIndex + 1) : border.get(0));
	}
	
	private boolean isInTheMiddle(Point2D before, Point2D current, Point2D after) {
		double dist = before.distance(after);
		double dist1 = before.distance(current);
		double dist2 = current.distance(after);
		return Math.abs(dist1 + dist2 - dist) < 0.05;
	}

	private void applyCrop() {
		model.clearSelectedTraces();

		MapField fld = new MapField(field);
		fld.setZoom(28);
		List<Point2D> border = getScreenPoligon(fld);
		
		List<SgyFile> slicedSgyFiles = new ArrayList<>();

		undoFiles.clear();
		undoFiles.addAll(model.getFileManager().getGprFiles());

		for (SgyFile file : model.getFileManager().getGprFiles()) {
			slicedSgyFiles.addAll(splitFile(file, fld, border));
			model.getProfileField(file).clear();
			model.publishEvent(new FileClosedEvent(this, file));
		}

		for (SgyFile file : model.getFileManager().getCsvFiles()) {
			slicedSgyFiles.addAll(splitFile(file, fld, border));
		}
		undoFiles.addAll(model.getFileManager().getCsvFiles());

		model.getFileManager().updateFiles(slicedSgyFiles);

		for (SgyFile sf : model.getFileManager().getCsvFiles()) {
			model.updateChart((CsvFile) sf);
		}

		for (SgyFile sf : model.getFileManager().getGprFiles()) {
			sf.updateTraces();
			//model.getProfileFieldByPattern(sf);//.clear();
		}
		model.publishEvent(new FileOpenedEvent(this, model.getFileManager().getGprFiles().stream().map(SgyFile::getFile).collect(Collectors.toList())));
		
		model.init();
	}

	private void applySplit() {
		ClickPlace mark = model.getSelectedTraceInCurrentChart();
		if (mark == null) {
			return;
		}
		Trace splitTrace = mark.getTrace();
		if (splitTrace == null) {
			return;
		}

		SgyFile file = splitTrace.getFile();
		int splitIndex = splitTrace.getIndexInFile();

		// first index of a new line
		if (isStartOfLine(file, splitIndex)) {
			// nothing to split
			return;
		}

		// change undo state
		undoFiles.clear();
		undoFiles.addAll(model.getFileManager().getFiles());

		if (file instanceof CsvFile csvFile) {
			splitCsvTrace(csvFile, splitIndex);
		} else if (file instanceof GprFile gprFile) {
			splitGprTrace(gprFile, splitIndex);
		}

		buttonUndo.setDisable(false);
	}

	private void undo() {
		model.clearSelectedTraces();

		if (!undoFiles.isEmpty()) {

			for (SgyFile file : model.getFileManager().getGprFiles()) {
				model.getProfileField(file).clear();
				model.publishEvent(new FileClosedEvent(this, file));
			}

			model.getFileManager().updateFiles(undoFiles);
			undoFiles.clear();
			
			for (SgyFile sf : model.getFileManager().getGprFiles()) {
				sf.updateTraces();
				//model.getProfileField(sf).clear();
			}

			for (SgyFile sf : model.getFileManager().getCsvFiles()) {
				sf.updateTraces();
				model.updateChart((CsvFile) sf);
			}

			model.publishEvent(new FileOpenedEvent(this, model.getFileManager().getGprFiles().stream().map(SgyFile::getFile).collect(Collectors.toList())));

			model.init();
		}
	}
	
	private boolean isTraceInsideSelection(MapField fld, List<Point2D> border, Trace trace) {
		return isInsideSelection(fld, border, trace.getLatLon());
	}

	private boolean isGeoDataInsideSelection(MapField fld, List<Point2D> border, GeoData geoData) {
		return isInsideSelection(fld, border, new LatLon(geoData.getLatitude(), geoData.getLongitude()));
	}

	private boolean isInsideSelection(MapField fld, List<Point2D> border, LatLon ll) {
		Point2D p = fld.latLonToScreen(ll);
		return inside(p, border);
	}

	private SgyFile generateSgyFileFrom(SgyFile sourceFile, List<Trace> traces, String partSuffix) {
		SgyFile copy = sourceFile.copyHeader();
		copy.setFile(getPartFile(sourceFile, partSuffix, sourceFile.getFile().getParentFile()));
		copy.setUnsaved(true);

		copy.setTraces(traces);

		// copy aux elements
		if (!traces.isEmpty()) {
			int begin = traces.getFirst().getIndexInFile();
			int end = traces.getLast().getIndexInFile();
			copy.setAuxElements(copyAuxObjects(sourceFile, copy, begin, end));
			/// TODO:
			if (copy instanceof DztFile) {
				DztFile dztfile = (DztFile) copy;
				dztfile.dzg = dztfile.dzg.cut(begin, end);
			}
			///
		}

		copy.updateTraces();
		return copy;
	}

	private File getPartFile(SgyFile file, String partSuffix, File folder) {
		String fileName = file.getFile().getName();
		String baseName = FileNames.removeExtension(fileName);
		String extension = FileNames.getExtension(fileName);

		return new File(folder, baseName + "_" + partSuffix + "." + extension);
	}

	private List<SgyFile> splitFile(SgyFile file, MapField field, List<Point2D> border) {
		if(file instanceof CsvFile) {
			return splitCsvFile((CsvFile)file, field, border);
		} else {
			return splitGprFile(file, field, border);
		}
	}

	private List<SgyFile> splitCsvFile(CsvFile csvFile, MapField field, List<Point2D> border) {
		CsvFile copy = csvFile.copy();
		copy.setUnsaved(true);

		// traces
		List<Trace> newTraces = new ArrayList<>();
		for (Trace trace: csvFile.getTraces()) {
			boolean inside = isTraceInsideSelection(field, border, trace);
			if (inside) {
				newTraces.add(trace);
			}
		}
		copy.setTraces(newTraces);

		// values
		// all filtered values
		List<GeoData> newGeoData = new ArrayList<>();
		// filtered values of the current line
		List<GeoData> lineGeoData = new ArrayList<>();
		// line index of the value in a source file
		int lineIndex = 0;
		// line index in a split file
		int splitLineIndex = 0;

		for(GeoData geoData : csvFile.getGeoData()) {
			boolean inside = isGeoDataInsideSelection(field, border, geoData);
			int dataLineIndex = geoData.getLineIndex();
			if (!inside || dataLineIndex != lineIndex) {
				if (!lineGeoData.isEmpty()) {
					if (isGoodForFile(lineGeoData)) { // filter too small lines
						for(GeoData gd: lineGeoData) {
							gd.setLineIndex(splitLineIndex);
						}
						splitLineIndex++;
						newGeoData.addAll(lineGeoData);
					}
					lineGeoData = new ArrayList<>();
				}
				lineIndex = dataLineIndex;
			}
			if (inside) {
				lineGeoData.add(new GeoData(geoData));
			}
		}
		// for last
		if (!lineGeoData.isEmpty()) {
			if (isGoodForFile(lineGeoData)) { // filter too small lines
				for(GeoData gd: lineGeoData) {
					gd.setLineIndex(splitLineIndex);
				}
				newGeoData.addAll(lineGeoData);
			}
		}
		copy.setGeoData(newGeoData);

		// aux elements
		copy.setAuxElements(csvFile.getAuxElements().stream()
				.filter(aux -> isInsideSelection(field, border, ((FoundPlace) aux).getLatLon()))
				.collect(Collectors.toList()));

		copy.updateTraces();

		return List.of(copy);
	}

	private List<SgyFile> splitGprFile(SgyFile file, MapField field, List<Point2D> border) {
		List<SgyFile> splitList = new ArrayList<>();
		List<Trace> sublist = new ArrayList<>();

		int part = 1;
		for (Trace trace : file.getTraces()) {
			boolean inside = isTraceInsideSelection(field, border, trace);
			if (inside) {
				sublist.add(trace);
			} else {
				if (!sublist.isEmpty()) {
					if (isGoodForFile(sublist)) { //filter too small files
						String partSuffix = String.format("%03d", part++);
						SgyFile subfile = generateSgyFileFrom(
								file, sublist, partSuffix);
						System.out.println("Target file name: " + subfile.getFile().getName());
						splitList.add(subfile);
					}
					sublist = new ArrayList<>();
				}
			}
		}

		//for last
		if (isGoodForFile(sublist)) {
			String partSuffix = String.format("%03d", part);
			SgyFile subfile = generateSgyFileFrom(file, sublist, partSuffix);
			splitList.add(subfile);
		}

		if (splitList.size() == 1) {
			SgyFile first = splitList.getFirst();
			first.setFile(file.getFile());
			first.updateTraces();
		}

		return splitList;
	}

	private boolean isStartOfLine(SgyFile file, int traceIndex) {
		Check.notNull(file);
		Check.condition(traceIndex >= 0);

		if (traceIndex == 0) {
			return true;
		}
		if (file instanceof CsvFile csvFile) {
			List<GeoData> values = csvFile.getGeoData();
			return values.get(traceIndex).getLineIndex()
					!= values.get(traceIndex - 1).getLineIndex();
		}
		return false;
	}

	private void splitCsvTrace(CsvFile csvFile, int splitIndex) {
		Check.notNull(csvFile);
		Check.condition(splitIndex >= 0);

		List<GeoData> values = csvFile.getGeoData();
		List<GeoData> newValues = new ArrayList<>(values.size());

		// copy values and fill missing line indices
		int lastLineIndex = 0;
		for (GeoData value : values) {
			GeoData newValue = new GeoData(value);
			int lineIndex = value.getLineIndex(-1);
			if (lineIndex == -1) {
				lineIndex = lastLineIndex;
				newValue.setLineIndex(lineIndex);
			}
			lastLineIndex = lineIndex;
			newValues.add(newValue);
		}

		// shift lines after a split trace
		int splitLine = newValues.get(splitIndex).getLineIndex();
		Set<Integer> linesToShift = new HashSet<>();
		linesToShift.add(splitLine);
		for (int i = splitIndex; i < newValues.size(); i++) {
			GeoData newValue = newValues.get(i);
			int lineIndex = newValue.getLineIndex();
			if (linesToShift.contains(lineIndex)) {
				newValue.setLineIndex(lineIndex + 1);
				linesToShift.add(lineIndex + 1);
			}
		}

		CsvFile copy = csvFile.copy();
		copy.setUnsaved(true);

		List<Trace> newTraces = csvFile.getTraces();

		copy.setTraces(newTraces);
		copy.setGeoData(newValues);
		copy.setAuxElements(new ArrayList<>(csvFile.getAuxElements()));
		copy.updateTraces();

		// clear selection
		model.clearSelectedTrace(model.getFileChart(csvFile));

		// refresh chart
		model.getFileManager().removeFile(csvFile);
		model.getFileManager().addFile(copy);
		model.updateChart(copy);

		// update model
		model.init();

		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
	}

	private void splitGprTrace(GprFile gprFile, int splitIndex) {
		Check.notNull(gprFile);
		Check.condition(splitIndex >= 0);

		// list of new/updated files
		List<SgyFile> generated = new ArrayList<>();
		// list of obsolete chart files that should be closed
		List<SgyFile> obsolete = new ArrayList<>();

		// reload tail to the chart
		Chart chart = model.getFileChart(gprFile);
		Check.notNull(chart);

		List<SgyFile> chartFiles = new ArrayList<>(chart.getFiles());
		chartFiles.sort(Comparator.comparing(f -> f.getFile().getName()));

		boolean isTail = false;
		for (SgyFile file : chartFiles) {
			boolean isSplitFile = file.equals(gprFile);
			if (isSplitFile) {
				isTail = true;
			}
			if (isTail) {
				obsolete.add(file);
				if (!isSplitFile) {
					// reload all files except current as is
					generated.add(file);
				}
			}
		}

		List<Trace> traces = gprFile.getTraces();
		boolean hasPartSuffix = FileNames.hasGprPartSuffix(
				gprFile.getFile().getName());
		SgyFile part1 = generateSgyFileFrom(
				gprFile,
				traces.subList(0, splitIndex),
				hasPartSuffix ? "1" : "001");
		generated.add(part1);
		SgyFile part2 = generateSgyFileFrom(
				gprFile,
				traces.subList(splitIndex, traces.size()),
				hasPartSuffix ? "2" : "002");
		generated.add(part2);

		generated.sort(Comparator.comparing(f -> f.getFile().getName()));

		// clear selection
		model.clearSelectedTrace(chart);

		// refresh chart
		for (SgyFile file : obsolete) {
			model.getFileManager().removeFile(file);
			model.publishEvent(new FileClosedEvent(this, file));
		}
		for (SgyFile file : generated) {
			model.getFileManager().addFile(file);
		}
		model.publishEvent(new FileOpenedEvent(this,
				generated.stream().map(SgyFile::getFile).toList()));

		// update model
		model.init();
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
	}

	public static List<BaseObject> copyAuxObjects(SgyFile file, SgyFile sgyFile, int begin, int end) {
		List<BaseObject> auxObjects = new ArrayList<>();				
		for (BaseObject au : file.getAuxElements()) {
			if (au.isFit(begin, end)) {
				BaseObject copy = au.copy(begin, sgyFile.getOffset());
				if (copy != null) {
					auxObjects.add(copy);
				}
			}
		}
		return auxObjects;
	}

	public boolean isGoodForFile(List<?> sublist) {
		return sublist.size() > 3;
	}
	
	private List<Point2D> getScreenPoligon(MapField fld) {

		List<Point2D> border = new ArrayList<>();
		for (LatLon ll : points) {
			border.add(fld.latLonToScreen(ll));
		}
		return border;
	}

	private boolean inside(Point2D p, List<Point2D> border) {
		
		boolean result = false;
		for (int i = 0; i < border.size(); i++) {
			Point2D pt1 = border.get(i);
			Point2D pt2 = border.get((i + 1) % border.size());
		
			if ((pt1.getY() > p.getY()) != (pt2.getY() > p.getY()) 
				&& (p.getX() 
					< (pt2.getX() - pt1.getX()) 
					* (p.getY() - pt1.getY()) 
					/ (pt2.getY() - pt1.getY()) 
					+ pt1.getX())) {
		         result = !result;
		    }		
		}
		
		return result;
	}

	@EventListener
    public void onFileOpened(FileOpenedEvent event) {
		//TODO: maybe we need other event for this
        clear();
        //undoFiles.clear();
        initButtons();
    }

	@EventListener
	private void fileSelected(FileSelectedEvent event) {
		Platform.runLater(this::updateSplit);
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isJustdraw()) {
			Platform.runLater(this::updateSplit);
		}
	}

	public List<Node> getToolNodes() {
		return Arrays.asList();
	}
	
	public List<Node> getToolNodes2() {
		
		initButtons();
		
		buttonCutMode.setOnAction(e -> updateCutMode());
		
		buttonCrop.setOnAction(e -> {
	    	applyCrop();
	    	
	    	buttonCutMode.setSelected(false);
	    	updateCutMode();
	    	buttonUndo.setDisable(false);
	    	eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
		});

		buttonSplit.setOnAction(e -> {
			applySplit();
		});
		
		buttonUndo.setOnAction(e -> {
	    	undo();

	    	buttonCutMode.setSelected(false);
	    	updateCutMode();
	    	buttonUndo.setDisable(true);
	    	eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.traceCut));
		});
		
		return Arrays.asList(buttonCutMode, buttonCrop, buttonSplit, buttonUndo);
	}

	public void initButtons() {
		buttonCutMode.setSelected(false);
		buttonCrop.setDisable(true);
		buttonSplit.setDisable(true);
		buttonUndo.setDisable(undoFiles.isEmpty());
	}

	private void updateCutMode() {
		if (buttonCutMode.isSelected()) {
    		init();
    		buttonCrop.setDisable(false);
    	} else {
    		clear();
    		buttonCrop.setDisable(true);
    	}
    	getListener().repaint();
	}

	private void updateSplit() {
		ClickPlace mark = model.getSelectedTraceInCurrentChart();
		buttonSplit.setDisable(mark == null);
	}

	public RepaintListener getListener() {
		return listener;
	}

	public void setListener(RepaintListener listener) {
		this.listener = listener;
	}
}

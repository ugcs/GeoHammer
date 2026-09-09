package com.ugcs.geohammer.map.layer.radar;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.ugcs.geohammer.map.RenderQueue;
import com.ugcs.geohammer.map.RepaintListener;
import com.ugcs.geohammer.map.layer.BaseLayer;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ActivationPolicy;
import com.ugcs.geohammer.model.ToolNode;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.util.PaletteBuilder;
import com.ugcs.geohammer.view.Listeners;
import javafx.geometry.Point2D;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.view.control.BaseCheckBox;
import com.ugcs.geohammer.view.control.BaseSlider;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

@Component
public class RadarMap extends BaseLayer {

	private static final int[] PALETTE = new PaletteBuilder().build();

	private static final double MIN_CIRCLE_THRESHOLD = 2.0;

	private final Model model;

	private final IntensityCalculator intensityCalculator;

	private final RadarSettings radarSettings = new RadarSettings();

	// intensities by file
	private final ConcurrentMap<TraceFile, double[]> intensities = new ConcurrentHashMap<>();

	private final AtomicBoolean intensitiesStale = new AtomicBoolean();

	private final RenderQueue renderQueue;

	// view

	private final ToggleButton showMapButtonAmp;

	private final BaseSlider gainTopSlider;

	private final BaseSlider gainBottomSlider;

	private final BaseSlider thresholdSlider;

	private final BaseSlider radiusSlider;

	private final BaseCheckBox autoGainCheckbox;

	public RadarMap(Model model, IntensityCalculator intensityCalculator) {
		this.model = model;
		this.intensityCalculator = intensityCalculator;

		renderQueue = new RenderQueue(model) {

			public void draw(BufferedImage image, MapField field) {
				createHiRes(field, image);
			}

			public void onReady() {
				RepaintListener listener = getRepaintListener();
				if (listener != null) {
					listener.repaint();
				}
			}
		};


		showMapButtonAmp = ResourceImageHolder.setButtonImage(ResourceImageHolder.LIGHT, new ToggleButton());
		showMapButtonAmp.setTooltip(new Tooltip("Toggle amplitude map layer"));
		showMapButtonAmp.setSelected(true);
		showMapButtonAmp.setOnAction(this::onShowMap);

		gainTopSlider = new TopGainSlider(radarSettings);
		Listeners.onChange(gainTopSlider.getSlider().valueProperty(), v -> onParameterChange());
		gainBottomSlider = new BottomGainSlider(radarSettings);
		Listeners.onChange(gainBottomSlider.getSlider().valueProperty(), v -> onParameterChange());
		thresholdSlider = new ThresholdSlider(radarSettings);
		Listeners.onChange(thresholdSlider.getSlider().valueProperty(), v -> onParameterChange());
		radiusSlider = new RadiusSlider(radarSettings);
		Listeners.onChange(radiusSlider.getSlider().valueProperty(), v -> {
			renderQueue.submit();
		});
		autoGainCheckbox = new AutoGainCheckBox(radarSettings);
		Listeners.onChange(autoGainCheckbox.getCheckBox().selectedProperty(), v -> onAutoGainToggle());

	}

	private void onParameterChange() {
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.adjusting));
	}

	private void onAutoGainToggle() {
		gainTopSlider.update();
		gainBottomSlider.update();
		thresholdSlider.update();
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.adjusting));
	}

	private void onShowMap(ActionEvent event) {
		setActive(showMapButtonAmp.isSelected());
		if (isActive()) {
			renderQueue.submit();
		} else {
			renderQueue.clear();
			getRepaintListener().repaint();
		}
	}

	public boolean isActive() {
		return radarSettings.isRadarMapVisible();
	}

	public void setActive(boolean active) {
		radarSettings.setRadarMapVisible(active);
	}

	@Override
	public void setSize(Dimension size) {
		renderQueue.setRenderSize(size);
	}

	//draw on the map window prepared image
	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		if (!isActive()) {
			return;
		}
		renderQueue.drawWithTransform(g2, currentField, renderQueue.getLastFrame());
	}
	
	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isTraceCut() || changed.isTraceValues()) {
			intensitiesStale.set(true);
		}
		
		if (changed.isAdjusting()) {
			intensitiesStale.set(true);
		}
		
		if (changed.isTraceCut() 
				|| changed.isTraceValues()
				|| changed.isZoom() 
				|| changed.isAdjusting() 
				|| changed.isMapscroll() 
				|| changed.isWindowresized()) {
			renderQueue.submit();
		}		
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		renderQueue.clear();
		renderQueue.submit();
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		if (event.getFile() instanceof TraceFile traceFile) {
			intensities.remove(traceFile);
		}
	}

	private void updateIntensities(boolean refresh) {
		for (TraceFile file : model.getFileManager().getGprFiles()) {
			double[] intensity = intensities.get(file);
			if (intensity == null || refresh) {
				intensity = intensityCalculator.getIntensity(file, radarSettings);
				intensities.put(file, intensity);
			}
		}
	}

	// prepare image in thread
	public void createHiRes(MapField field, BufferedImage img) {
		boolean refresh = intensitiesStale.getAndSet(false);
		updateIntensities(refresh);

		DblArray buffer = new DblArray(img.getWidth(), img.getHeight());
		drawCircles(field, buffer);
		buffer.toImg(img, PALETTE);
	}

	public void drawCircles(MapField field, DblArray buffer) {
		for (TraceFile file : model.getFileManager().getGprFiles()) {
			drawFileCircles(file, field, buffer);
		}
	}

	public void drawFileCircles(TraceFile file, MapField field, DblArray buffer) {
		double[] intensity = intensities.get(file);
		if (intensity == null) {
			return;
		}

		int radius = radarSettings.getRadius();
		int centerX = buffer.getWidth() / 2;
		int centerY = buffer.getHeight() / 2;

		List<Trace> traces = file.getTraces();
		for (int i = 0; i < file.numTraces(); i++) {
			Trace trace = traces.get(i);
			double alpha = intensity[i];

			if (alpha > MIN_CIRCLE_THRESHOLD) {				
				Point2D p = field.latLonToScreen(trace.getLatLon());
				buffer.drawCircle(
					(int) p.getX() + centerX,
					(int) p.getY() + centerY,
					radius,
					alpha);
			}
		}
	}
	
	public List<Node> getControlNodes(SgyFile dataFile) {
		VBox vertBox = new VBox();
		vertBox.getChildren().addAll(
			List.of(
				autoGainCheckbox,
				gainTopSlider,
				gainBottomSlider,
				thresholdSlider,
				radiusSlider
			));

		return List.of(vertBox);
	}
	
	@Override
	public List<ToolNode> getToolNodes() {
		return List.of(
				new ToolNode(showMapButtonAmp, ActivationPolicy.fileSelected()));
	}
}

package com.ugcs.geohammer.map.layer.radar;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import com.ugcs.geohammer.map.RenderQueue;
import com.ugcs.geohammer.map.layer.BaseLayer;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.ActivationPolicy;
import com.ugcs.geohammer.model.ToolNode;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import javafx.geometry.Point2D;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.service.gpr.CommandRegistry;
import com.ugcs.geohammer.service.gpr.RadarMapScan;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.model.ScanProfile;
import com.ugcs.geohammer.view.control.AutoGainCheckbox;
import com.ugcs.geohammer.view.control.BaseCheckBox;
import com.ugcs.geohammer.view.control.BaseSlider;
import com.ugcs.geohammer.view.control.BottomGainSlider;
import com.ugcs.geohammer.view.control.TopGainSlider;
import com.ugcs.geohammer.view.control.RadiusSlider;
import com.ugcs.geohammer.view.control.ThresholdSlider;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

@Component
public class RadarMap extends BaseLayer implements InitializingBean {

	private static final double MIN_CIRCLE_THRESHOLD = 2.0;

	private final CommandRegistry commandRegistry;

	private final Model model;

	public RadarMap(CommandRegistry commandRegistry, Model model) {
		this.commandRegistry = commandRegistry;
		this.model = model;
	}

	private BaseSlider gainTopSlider;
	private BaseSlider gainBottomSlider;
	private BaseSlider thresholdSlider;
	private BaseSlider radiusSlider;
	private BaseCheckBox autoGainCheckbox;

	private ArrayBuilder scaleArrayBuilder;
	private ArrayBuilder autoArrayBuilder;

	private final Settings radarMapSettings = new Settings();
	
	private EventHandler<ActionEvent> showMapListener = new EventHandler<ActionEvent>() {
		
		@Override
		public void handle(ActionEvent event) {
			setActive(showMapButtonAmp.isSelected());

			if (isActive()) {
				q.submit();
			} else {
				q.clear();
				getRepaintListener().repaint();
			}
		}
	};
	
	private ToggleButton showMapButtonAmp = ResourceImageHolder.setButtonImage(ResourceImageHolder.LIGHT, new ToggleButton());

	{
		showMapButtonAmp.setTooltip(new Tooltip("Toggle amplitude map layer"));
		showMapButtonAmp.setSelected(true);
		showMapButtonAmp.setOnAction(showMapListener);
	}
	
	private ChangeListener<Number> sliderListener = new ChangeListener<Number>() {
		@Override
		public void changed(ObservableValue<? extends Number> observable, 
				Number oldValue, Number newValue) {
			q.submit();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.adjusting));
		}
	};
	
	public boolean isActive() {
		return radarMapSettings.isRadarMapVisible();
	}

	public void setActive(boolean active) {
		radarMapSettings.setRadarMapVisible(active);
	}
	
	private ChangeListener<Boolean> autoGainListener = new ChangeListener<Boolean>() {
		@Override
		public void changed(ObservableValue<? extends Boolean> observable, 
				Boolean oldValue, Boolean newValue) {
			
			gainBottomSlider.updateUI();
			gainTopSlider.updateUI();
			thresholdSlider.updateUI();

			q.submit();
		}
	};
	
	RenderQueue q;
	
	public void initQ() {
		q = new RenderQueue(model) {
			public void draw(BufferedImage image, MapField field) {
				createHiRes(field, image);
			}
			
			public void onReady() {
				getRepaintListener().repaint();
			}			
		};
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {		
		
		autoArrayBuilder = new MedianScaleBuilder(model);
		scaleArrayBuilder = new ScaleArrayBuilder(radarMapSettings);

		Settings settings = radarMapSettings;
		gainTopSlider = new TopGainSlider(settings, sliderListener);
		gainBottomSlider = new BottomGainSlider(settings, sliderListener);
		thresholdSlider = new ThresholdSlider(settings, sliderListener);
		radiusSlider = new RadiusSlider(settings, sliderListener);
		
		autoGainCheckbox = new AutoGainCheckbox(settings, autoGainListener);
				
		initQ();
	}

	@Override
	public void setSize(Dimension size) {
		q.setRenderSize(size);
	}

	//draw on the map window prepared image
	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		
		if (!isActive()) {
			return;
		}
				
		q.drawWithTransform(g2, currentField, q.getLastFrame());
	}
	
	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isTraceCut()
				|| changed.isTraceValues() 
				) {
			autoArrayBuilder.clear();
			scaleArrayBuilder.clear();
		}
		
		if (changed.isAdjusting()) {
			//autoArrayBuilder.clear();
			scaleArrayBuilder.clear();
		}
		
		if (changed.isTraceCut() 
				|| changed.isTraceValues()
				|| changed.isZoom() 
				|| changed.isAdjusting() 
				|| changed.isMapscroll() 
				|| changed.isWindowresized()) {

			q.submit();
		}		
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
			autoArrayBuilder.clear();
			scaleArrayBuilder.clear();
			q.clear();
			q.submit();
	}

	// prepare image in thread
	public void createHiRes(MapField field, BufferedImage img) {
		DblArray da = new DblArray(img.getWidth(), img.getHeight());

		// fill file.amplScan
		commandRegistry.runForGprFiles(
				model.getFileManager().getGprFiles(),
				new RadarMapScan(getArrayBuilder(), model));
		int[] palette = DblArray.paletteAmp;

		drawCircles(field, da);
		da.toImg(img, palette);
	}

	public void drawCircles(MapField field, DblArray da) {
		for (TraceFile file : model.getFileManager().getGprFiles()) {
			
			ScanProfile profile = getFileScanProfile(file);
			
			List<Trace> traces = file.getTraces();
			if (profile != null) {
				drawFileCircles(field, da, file, profile, traces);
			}
		}
	}

	public ScanProfile getFileScanProfile(TraceFile file) {
        return file.getAmplScan();
	}

	public void drawFileCircles(MapField field, DblArray da, SgyFile file, 
			ScanProfile profile, List<Trace> traces) {
		
		int radius = radarMapSettings.getRadius();
		int centerX = da.getWidth() / 2;
		int centerY = da.getHeight() / 2;
		
		for (int i = 0; i < file.numTraces(); i++) {
			Trace trace = traces.get(i);
			
			double alpha = profile.intensity[i];
			int effectRadius = 
					(int) (profile.radius != null ? profile.radius[i] : radius);
			
			if (alpha > MIN_CIRCLE_THRESHOLD) {				
			
				Point2D p = field.latLonToScreen(trace.getLatLon());
				
				da.drawCircle(
					(int) p.getX() + centerX, 
					(int) p.getY() + centerY, 
					effectRadius, 
					alpha);
				
			}
		}
	}
	
	public List<Node> getControlNodes(SgyFile dataFile) {
		VBox vertBox = new VBox();
		vertBox.getChildren().addAll(
			List.of(
				autoGainCheckbox.produce(),
				gainTopSlider.produce(),
				gainBottomSlider.produce(),
				thresholdSlider.produce(),
				radiusSlider.produce()
			));

		return List.of(vertBox);
	}
	
	@Override
	public List<ToolNode> getToolNodes() {
		return List.of(
				new ToolNode(showMapButtonAmp, ActivationPolicy.fileSelected()));
	}

	private ArrayBuilder getArrayBuilder() {
		if (radarMapSettings.isAutoGain()) {
			return autoArrayBuilder;
		} else {
			return scaleArrayBuilder;
		}
	}
}

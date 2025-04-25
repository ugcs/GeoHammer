package com.ugcs.gprvisualizer.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import javafx.geometry.Point2D;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.commands.CommandRegistry;
import com.ugcs.gprvisualizer.app.commands.RadarMapScan;
import com.ugcs.gprvisualizer.gpr.ArrayBuilder;
import com.ugcs.gprvisualizer.gpr.DblArray;
import com.ugcs.gprvisualizer.gpr.MedianScaleBuilder;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.ScaleArrayBuilder;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.gpr.Settings.RadarMapMode;
import com.ugcs.gprvisualizer.math.ScanProfile;
import com.ugcs.gprvisualizer.ui.AutoGainCheckbox;
import com.ugcs.gprvisualizer.ui.BaseCheckBox;
import com.ugcs.gprvisualizer.ui.BaseSlider;
import com.ugcs.gprvisualizer.ui.GainBottomSlider;
import com.ugcs.gprvisualizer.ui.GainTopSlider;
import com.ugcs.gprvisualizer.ui.RadiusSlider;
import com.ugcs.gprvisualizer.ui.ThresholdSlider;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

@Component
public class RadarMap extends BaseLayer implements InitializingBean {

	private static final double MIN_CIRCLE_THRESHOLD = 2.0;

	private static final String BORDER_STYLING = """
		-fx-border-color: gray; 
		-fx-border-insets: 5;
		-fx-border-width: 1;
		-fx-border-style: solid;
		""";

	private final CommandRegistry commandRegistry;

	private final Model model;

	private final MapView mapView;

	public RadarMap(CommandRegistry commandRegistry, Model model, MapView mapView) {
		this.commandRegistry = commandRegistry;
		this.model = model;
		this.mapView = mapView;
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

			//setActive(showMapButtonAlg.isSelected() || showMapButtonAmp.isSelected());
			setActive(showMapButtonAmp.isSelected());
			//vertBox.setDisable(!isActive());
			
			
			/*if (showMapButtonAlg.isSelected()) {
				model.getSettings().radarMapMode = RadarMapMode.SEARCH;
			}*/

			if (showMapButtonAmp.isSelected()) {
                radarMapSettings.radarMapMode = RadarMapMode.AMPLITUDE;
                radarMapSettings.getHyperliveview().setFalse();
			}
			
			if (isActive()) {
				q.add();
				
			} else {
				q.clear();
				getRepaintListener().repaint();
			}
			
		}
	};
	
	ToggleGroup group = new ToggleGroup();
	private ToggleButton showMapButtonAmp = ResourceImageHolder.setButtonImage(ResourceImageHolder.LIGHT, new ToggleButton());
			//new ToggleButton("", ResourceImageHolder.getImageView("light-20.png"));
	
	{
		showMapButtonAmp.setTooltip(new Tooltip("Toggle amplitude map layer"));
		showMapButtonAmp.setSelected(true);
		showMapButtonAmp.setOnAction(showMapListener);
		showMapButtonAmp.setToggleGroup(group);
	}
	
	//private ToggleButton showMapButtonAlg = 
	//		new ToggleButton("", ResourceImageHolder.getImageView("floodlight-20.png"));
	
	/*{
		showMapButtonAlg.setTooltip(new Tooltip("Toggle algorithm search layer"));
		showMapButtonAlg.setSelected(false);
		showMapButtonAlg.setOnAction(showMapListener);
		showMapButtonAlg.setToggleGroup(group);
	}*/
	
	/*public void selectAlgMode() {
		showMapButtonAlg.setSelected(true);		
		showMapListener.handle(null);
	}*/
	
	private ChangeListener<Number> sliderListener = new ChangeListener<Number>() {
		@Override
		public void changed(ObservableValue<? extends Number> observable, 
				Number oldValue, Number newValue) {
			q.add();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.adjusting));
		}
	};
	
	public boolean isActive() {
		return radarMapSettings.isRadarMapVisible;
	}

	public void setActive(boolean active) {
		radarMapSettings.isRadarMapVisible = active;
	}
	
	private ChangeListener<Boolean> autoGainListener = new ChangeListener<Boolean>() {
		@Override
		public void changed(ObservableValue<? extends Boolean> observable, 
				Boolean oldValue, Boolean newValue) {
			
			gainBottomSlider.updateUI();
			gainTopSlider.updateUI();
			thresholdSlider.updateUI();

			q.add();
		}
	};
	
	ThrQueue q;
	
	public void initQ() {
		q = new ThrQueue(model, mapView) {
			protected void draw(BufferedImage backImg, MapField field) {
				
				createHiRes(field, backImg);
			}
			
			public void ready() {
				getRepaintListener().repaint();
			}			
			
		};
	}
	
	@Override
	public void afterPropertiesSet() throws Exception {		
		
		autoArrayBuilder = new MedianScaleBuilder(model);
		scaleArrayBuilder = new ScaleArrayBuilder(radarMapSettings);

		Settings settings = radarMapSettings;
		gainTopSlider = new GainTopSlider(settings, sliderListener);
		gainBottomSlider = new GainBottomSlider(settings, sliderListener);
		thresholdSlider = new ThresholdSlider(settings, sliderListener);
		radiusSlider = new RadiusSlider(settings, sliderListener);
		
		autoGainCheckbox = new AutoGainCheckbox(settings, autoGainListener);
				
		initQ();
	}
	
	//draw on the map window prepared image
	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		
		if (!isActive()) {
			return;
		}
				
		q.drawImgOnChangedField(g2, currentField, q.getFront());
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
			
			//System.out.println("RadarMap start asinq");
			q.add();
		}		
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
			autoArrayBuilder.clear();
			scaleArrayBuilder.clear();
			q.clear();
			q.add();
	}

	// prepare image in thread
	public void createHiRes(MapField field, BufferedImage img) {
		
				
		DblArray da = new DblArray(img.getWidth(), img.getHeight());

		int[] palette;
		if (radarMapSettings.radarMapMode == RadarMapMode.AMPLITUDE) {
			// fill file.amplScan
			commandRegistry.runForGprFiles(new RadarMapScan(getArrayBuilder(), model));
			palette = DblArray.paletteAmp;
		} else {
			palette = DblArray.paletteAlg;
		}

		drawCircles(field, da);
		da.toImg(img, palette);
	}

	public void drawCircles(MapField field, DblArray da) {
		for (SgyFile file : model.getFileManager().getGprFiles()) {
			
			ScanProfile profile = getFileScanProfile(file);
			
			List<Trace> traces = file.getTraces();
			if (profile != null) {
				drawFileCircles(field, da, file, profile, traces);
			}
		}
	}

	public ScanProfile getFileScanProfile(SgyFile file) {
		ScanProfile profile;
		if (radarMapSettings.radarMapMode == RadarMapMode.AMPLITUDE) {
			profile = file.amplScan;
		} else {
			profile = file.algoScan;
		}
		return profile;
	}

	public void drawFileCircles(MapField field, DblArray da, SgyFile file, 
			ScanProfile profile, List<Trace> traces) {
		
		int radius = radarMapSettings.radius;
		int centerX = da.getWidth() / 2;
		int centerY = da.getHeight() / 2;
		
		for (int i = 0; i < file.size(); i++) {
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
	public List<Node> getToolNodes() {
		return List.of(
			showMapButtonAmp
		);

		//return Arrays.asList(
		//	showMapButtonAmp, showMapButtonAlg);	
		
	}

	private ArrayBuilder getArrayBuilder() {
		if (radarMapSettings.autogain) {
			return autoArrayBuilder;
		} else {
			return scaleArrayBuilder;
		}
	}

}

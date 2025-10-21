package com.ugcs.gprvisualizer.draw;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import javafx.geometry.Point2D;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.scene.Node;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

@Component
public class SatelliteMap extends BaseLayer implements InitializingBean {

	private final Model model;
	private final Status status;
	private final MapView mapView;
    private final PrefSettings prefSettings;

    public SatelliteMap(Model model, Status status, MapView mapView, PrefSettings prefSettings) {
		this.model = model;
		this.status = status;
		this.mapView = mapView;
        this.prefSettings = prefSettings;
    }

	private LatLon click;

	private ThrQueue recalcQueue;

	@Override
	public void afterPropertiesSet() throws Exception {
		recalcQueue = new ThrQueue(model, mapView) {
			protected void draw(BufferedImage backImg, MapField field) {
				if (field.getMapProvider() != null) {
					this.backImg = field.getMapProvider().loading(field);
				}
			}

			public void ready() {
				getRepaintListener().repaint();
			}

			protected void actualizeBackImg() {

			}
		};

		optionsMenuBtn.getItems().addAll(menuItem1, menuItem2, menuItem3);
		ToggleGroup toggleGroup = new ToggleGroup();
		menuItem1.setToggleGroup(toggleGroup);
		menuItem2.setToggleGroup(toggleGroup);
		menuItem3.setToggleGroup(toggleGroup);

		menuItem1.setOnAction(e -> {
			model.getMapField().setMapProvider(new GoogleMapProvider());
			setActive(model.getMapField().getMapProvider() != null);
			recalcQueue.clear();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
		});

		menuItem2.setOnAction(e -> {
			model.getMapField().setMapProvider(new HereMapProvider(prefSettings.getSetting("maps", "here_api_key")));
			setActive(model.getMapField().getMapProvider() != null);
			recalcQueue.clear();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
		});

		menuItem2.setSelected(true);
		menuItem2.fire();

		menuItem3.setOnAction(e -> {
			model.getMapField().setMapProvider(null);
			setActive(model.getMapField().getMapProvider() != null);
			recalcQueue.clear();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
		});
	}	

//	private EventHandler<ActionEvent> showMapListener = new EventHandler<ActionEvent>() {
//		@Override
//		public void handle(ActionEvent event) {
//			setActive(showLayerCheckbox.isSelected());
//			if (isActive()) {
//				q.add();
//			} else {
//				getRepaintListener().repaint();
//			}
//				
//		}
//	};



	RadioMenuItem menuItem1 = new RadioMenuItem("google maps");
	RadioMenuItem menuItem2 = new RadioMenuItem("here.com");
	RadioMenuItem menuItem3 = new RadioMenuItem("turn off");

	private final MenuButton optionsMenuBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.MAP, new MenuButton());

	{
		///optionsMenuBtn.setStyle("padding-left: 2px; padding-right: 2px");

	}

//	private ToggleButton showLayerCheckbox = 
//			new ToggleButton("", ResourceImageHolder.getImageView("gmap-20.png"));
//	
//	{
//		//boolean apiExists = StringUtils.isNotBlank(GOOGLE_API_KEY);
//		
//		showLayerCheckbox.setTooltip(new Tooltip("Toggle satellite map layer"));
//		//showLayerCheckbox.setDisable(!apiExists);
//		//showLayerCheckbox.setSelected(apiExists);
//		showLayerCheckbox.setOnAction(showMapListener);
//	}

	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		ThrFront front = recalcQueue.getFront();

		if (front != null && isActive()) {

			recalcQueue.drawImgOnChangedField(g2, currentField, front);
		}		

		if (click != null) {
			Point2D p = currentField.latLonToScreen(click);

			g2.drawOval((int) p.getX() - 3, (int) p.getY() - 3, 7, 7);
		}
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (changed.isZoom()) {
			if (model.isActive()) {
				//loadMap();
				recalcQueue.add();
			} else {
				recalcQueue.clear();
			}			
		}		
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		if (model.isActive()) {
			//loadMap();
			recalcQueue.add();
		} else {
			recalcQueue.clear();
		}
	}

	MapField dragField = null;

	@Override
	public boolean mousePressed(Point2D point) {

		if (!model.getFileManager().isActive()) {
			return false;
		}

		dragField = new MapField(model.getMapField());

		click = model.getMapField().screenTolatLon(point);

		status.showMessage(click.toString(), "Coordinates");

		getRepaintListener().repaint();

		return true;
	}

	@Override
	public boolean mouseRelease(Point2D point) {

		dragField = null;
		click = null;

		model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapscroll));

		return true;
	}

	@Override
	public boolean mouseMove(Point2D point) {

		if (dragField == null) {
			return false;
		}

		LatLon newCenter = dragField.screenTolatLon(point);		
		double lat = dragField.getSceneCenter().getLatDgr() 
				+ click.getLatDgr() - newCenter.getLatDgr();
		double lon = dragField.getSceneCenter().getLonDgr() 
				+ click.getLonDgr() - newCenter.getLonDgr();

		model.getMapField().setSceneCenter(new LatLon(lat, lon));

		getRepaintListener().repaint();

		return true;
	}

	@Override
	public List<Node> getToolNodes() {
		HBox cnt = new HBox(/*showLayerCheckbox,*/ optionsMenuBtn);
		return Arrays.asList(cnt);
	}

}

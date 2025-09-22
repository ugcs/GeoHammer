package com.ugcs.gprvisualizer.draw.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.draw.BaseLayer;
import com.ugcs.gprvisualizer.draw.map.provider.GoogleMapProvider;
import com.ugcs.gprvisualizer.draw.map.provider.HereMapProvider;
import com.ugcs.gprvisualizer.draw.map.provider.OffMapProvider;
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
	private final List<MapProvider> providers;
	private final MenuButton optionsMenuBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.MAP, new MenuButton());

	private LatLon click;
	private ThrQueue recalcQueue;

    public SatelliteMap(Model model, Status status, MapView mapView, PrefSettings prefSettings) {
		this.model = model;
		this.status = status;
		this.mapView = mapView;
		this.providers = Arrays.asList(
				new GoogleMapProvider(),
				new HereMapProvider(prefSettings),
				new OffMapProvider()
		);
    }

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

		buildProvidersMenu();

	}

	private void buildProvidersMenu() {
		optionsMenuBtn.getItems().clear();
		ToggleGroup group = new ToggleGroup();
		RadioMenuItem toSelect = null;

		for (MapProvider p : providers) {
			RadioMenuItem item = new RadioMenuItem(p.name());
			item.setToggleGroup(group);
			item.setOnAction(e -> applyProvider(p));
			optionsMenuBtn.getItems().add(item);
			if (toSelect == null && p.isDefault()) toSelect = item;
		}
		if (toSelect == null && !optionsMenuBtn.getItems().isEmpty()) {
			toSelect = (RadioMenuItem) optionsMenuBtn.getItems().getFirst();
		}
		if (toSelect != null) {
			toSelect.setSelected(true);
			toSelect.fire();
		}
	}

	private void applyProvider(MapProvider provider) {
		provider.onSelect(model);
		setActive(model.getMapField().getMapProvider() != null);
		recalcQueue.clear();
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
	}

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
		HBox cnt = new HBox(optionsMenuBtn);
		return List.of(cnt);
	}

}

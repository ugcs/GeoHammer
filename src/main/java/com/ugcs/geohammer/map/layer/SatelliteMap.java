package com.ugcs.geohammer.map.layer;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.map.RenderQueue;
import com.ugcs.geohammer.map.provider.GoogleMapProvider;
import com.ugcs.geohammer.map.provider.HereMapProvider;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.PrefSettings;
import javafx.geometry.Point2D;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.model.Model;

import javafx.scene.Node;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

@Component
public class SatelliteMap extends BaseLayer implements InitializingBean {

	private final Model model;
	private final Status status;
    private final PrefSettings prefSettings;

	private int lastTileZoom = -1;

	public SatelliteMap(Model model, Status status, PrefSettings prefSettings) {
		this.model = model;
		this.status = status;
        this.prefSettings = prefSettings;
    }

	private LatLon click;

	private RenderQueue recalcQueue;

	@Override
	public void afterPropertiesSet() throws Exception {
		recalcQueue = new RenderQueue(model) {
			public void draw(BufferedImage image, MapField field) {
				if (field.getMapProvider() != null) {
					this.setRenderImage(field.getMapProvider().loadimg(field));
				}
			}

			public void onReady() {
				getRepaintListener().repaint();
			}

			@Override
			protected void actualizeRenderImage() {
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
			clearTiles();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
		});

		menuItem2.setOnAction(e -> {
			model.getMapField().setMapProvider(new HereMapProvider(prefSettings.getString("maps", "here_api_key")));
			setActive(model.getMapField().getMapProvider() != null);
			clearTiles();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
		});

		menuItem2.setSelected(true);
		menuItem2.fire();

		menuItem3.setOnAction(e -> {
			model.getMapField().setMapProvider(null);
			setActive(model.getMapField().getMapProvider() != null);
			clearTiles();
			model.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
		});
	}	

	RadioMenuItem menuItem1 = new RadioMenuItem("google maps");
	RadioMenuItem menuItem2 = new RadioMenuItem("here.com");
	RadioMenuItem menuItem3 = new RadioMenuItem("turn off");

	private final MenuButton optionsMenuBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.MAP, new MenuButton());

	@Override
	public void setSize(Dimension size) {
		recalcQueue.setRenderSize(size);
	}

	@Override
	public void draw(Graphics2D g2, MapField currentField) {
		RenderQueue.Frame front = recalcQueue.getLastFrame();

		if (front != null && isActive()) {
			recalcQueue.drawWithTransform(g2, currentField, front);
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
				submitTiles();
			} else {
				clearTiles();
			}
		}		
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		if (model.isActive()) {
			submitTiles();
		} else {
			clearTiles();
		}
	}

	private void submitTiles() {
		int intZoom = (int)model.getMapField().getZoom();
		if (intZoom != lastTileZoom) {
			recalcQueue.submit();
			lastTileZoom = intZoom;
		}
	}

	private void clearTiles() {
		recalcQueue.clear();
		lastTileZoom = -1;
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

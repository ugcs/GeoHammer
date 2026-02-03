package com.ugcs.geohammer.map;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.ugcs.geohammer.map.layer.MapRuler;
import com.ugcs.geohammer.map.layer.TraceCutter;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.SettingsView;
import com.ugcs.geohammer.map.layer.BaseLayer;
import com.ugcs.geohammer.map.layer.GpsTrack;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.map.layer.Layer;
import com.ugcs.geohammer.map.layer.QualityLayer;
import com.ugcs.geohammer.map.layer.radar.RadarMap;
import com.ugcs.geohammer.map.layer.SatelliteMap;
import com.ugcs.geohammer.model.event.WhatChanged;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.map.layer.FoundTracesLayer;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.Model;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import com.ugcs.geohammer.model.event.FileOpenedEvent;

import javax.annotation.Nullable;

@Component
public class MapView implements InitializingBean {

	private static final Logger log = LoggerFactory.getLogger(MapView.class);

	private static final String NO_GPS_TEXT = "There are no coordinates in files";

	private static AtomicInteger entercount = new AtomicInteger(0);

	@Autowired
	private TraceCutter traceCutter;

	@Autowired
	private MapRuler mapRuler;
	
	@Autowired
	private Model model;
	
	@Autowired
	private ApplicationEventPublisher eventPublisher;
	
	@Autowired
	private SatelliteMap satelliteMap;

	@Autowired
	private RadarMap radarMap;

	@Autowired
	private GpsTrack gpsTrackMap;

	@Autowired
	private ZoomControlsView zoomControlsView;
	
	private GridLayer gridLayer;

	@Autowired
	private QualityLayer qualityLayer;

	@Autowired
	private List<BaseLayer> baseLayers;

	@Autowired
	private SettingsView settingsView;

	private List<Layer> layers = new ArrayList<>();

	private ImageView imageView = new ImageView();
	private BufferedImage img;

	private ToolBar toolBar = new ToolBar();
	private Dimension windowSize = new Dimension();

	private final BorderPane root = new BorderPane();
	@Nullable private DistanceLabelPane distanceLabelPane;

	private RepaintListener listener = this::updateUI;

	private EventHandler<MouseEvent> mousePressHandler = event -> {
        if (!isGpsPresent()) {
            return;
        }

        Point2D p = getLocalCoords(event);

        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            try {
                if (layer.mousePressed(p)) {
                    return;
                }
            } catch (Exception e) {
                log.error("Error", e);
            }
        }
    };

	private EventHandler<MouseEvent> mouseReleaseHandler = event -> {
        if (!isGpsPresent()) {
            return;
        }
        Point2D p = getLocalCoords(event);
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.mouseRelease(p)) {
                return;
            }
        }
    };

	private EventHandler<MouseEvent> mouseMoveHandler = event -> {
        if (!isGpsPresent()) {
            return;
        }
        Point2D p = getLocalCoords(event);
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer layer = layers.get(i);
            if (layer.mouseMove(p)) {
                return;
            }
        }
    };

	private EventHandler<MouseEvent> mouseClickHandler = event -> {
		if (!isGpsPresent()) {
			return;
		}
		if (event.getClickCount() == 1 && event.getButton() == MouseButton.SECONDARY) {
			Point2D point = getLocalCoords(event);
			for (Layer layer : layers) {
				if (layer.mouseRightClick(point)) {
					event.consume();
					break;
				}
			}
		}
		if (event.getClickCount() == 2) {
			Point2D p = getLocalCoords(event);
			LatLon location = model.getMapField().screenTolatLon(p);
			model.selectNearestTrace(location);
		}
    };

	@Override
	public void afterPropertiesSet() throws Exception {
		
		radarMap.setRepaintListener(listener);
		
		satelliteMap.setRepaintListener(listener);
		
		gpsTrackMap.setRepaintListener(listener);

		gridLayer.setRepaintListener(listener);

		qualityLayer.setRepaintListener(listener);

		layers.add(satelliteMap);
		layers.add(radarMap);
		layers.add(gridLayer);
		layers.add(qualityLayer);
		layers.add(gpsTrackMap);
		layers.add(new FoundTracesLayer(model));

		//TODO: bad style
		traceCutter.setListener(listener);
		layers.add(traceCutter);

		mapRuler.setRepaintCallback(() -> listener.repaint());
		layers.add(mapRuler);

		setLayerSizes();
		initImageView();
	}

	private void setLayerSizes() {
		for (Layer layer : layers) {
			if (layer instanceof BaseLayer baseLayer) {
				baseLayer.setSize(windowSize);
			}
		}
	}

	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (!isGpsPresent()) {
			return;
		}

		if (changed.isJustdraw() || changed.isTraceSelected()) {
			updateUI();
		}
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		toolBar.setDisable(!model.isActive() || !isGpsPresent());
		updateUI();
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		toolBar.setDisable(!model.isActive() || !isGpsPresent());
		updateUI();
	}

	@Autowired
	public void setGridLayer(GridLayer gridLayer) {
		this.gridLayer = gridLayer;
	}
	
	private boolean isGpsPresent() {
		return model.getMapField().isActive();
	}

	private void initImageView() {
		//ZOOM
		imageView.setOnScroll(event -> {
			
			if (!isGpsPresent()) {
				return;
			}
			
			Point2D p = getLocalCoords(event.getSceneX(), event.getSceneY());
			LatLon ll = model.getMapField().screenTolatLon(p);

			double zoomDelta = 0.1 * Math.clamp(event.getDeltaY(), -3, 3);
	    	double zoom = model.getMapField().getZoom() + zoomDelta;
			model.getMapField().setZoom(zoom);
	    	
	    	Point2D p2 = model.getMapField().latLonToScreen(ll);
	    	Point2D pdist = new Point2D(p2.getX() - p.getX(), p2.getY() - p.getY());
	    	
	    	LatLon sceneCenter = model.getMapField().screenTolatLon(pdist);			
			model.getMapField().setSceneCenter(sceneCenter);

			eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.mapzoom));
	    });		
	
		imageView.setOnMouseClicked(mouseClickHandler);
		imageView.setOnMousePressed(mousePressHandler);
		imageView.setOnMouseReleased(mouseReleaseHandler);
		
		imageView.addEventFilter(MouseEvent.DRAG_DETECTED, new EventHandler<MouseEvent>() {
		    @Override
		    public void handle(MouseEvent mouseEvent) {
		    	
		    	imageView.startFullDrag();
		    }
		});		
		imageView.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseMoveHandler);
		imageView.addEventFilter(MouseDragEvent.MOUSE_DRAG_RELEASED, 
				new EventHandler<MouseDragEvent>() {
			@Override
			public void handle(MouseDragEvent event) {

				event.consume();
			}
		});
	}

	public Node getCenter() {
		configureToolBar();

		Pane mainPane = createMainPane();

		distanceLabelPane = new DistanceLabelPane(mapRuler, this::updateUI, this::updateDistanceLabelPaneVisibility);
		updateDistanceLabelPaneVisibility();

		initZoomControls(mainPane);

		root.setTop(toolBar);
		root.setCenter(mainPane);

		return root;
	}

	private void configureToolBar() {
		toolBar.setDisable(true);

		toolBar.getItems().addAll(settingsView.getToolNodes());

		toolBar.getItems().add(createFixedWidthSpacer());

		toolBar.getItems().addAll(traceCutter.getToolNodes2());
		toolBar.getItems().addAll(mapRuler.buildToolNodes());

		toolBar.getItems().add(createFlexibleSpacer());

		toolBar.getItems().addAll(getToolNodes());
	}

	private Pane createMainPane() {
		Pane pane = new Pane();
		pane.getChildren().add(imageView);

		ChangeListener<Number> sizeListener = (obs, oldVal, newVal) ->
				setSize((int) pane.getWidth(), (int) pane.getHeight());

		pane.widthProperty().addListener(sizeListener);
		pane.heightProperty().addListener(sizeListener);

		return pane;
	}

	private void initZoomControls(Pane pane) {
		pane.widthProperty().addListener((obs, oldVal, newVal) ->
				zoomControlsView.adjustX(newVal.doubleValue()));
		pane.heightProperty().addListener((obs, oldVal, newVal) ->
				zoomControlsView.adjustY(newVal.doubleValue()));

		zoomControlsView.adjustX(pane.getWidth());
		zoomControlsView.adjustY(pane.getHeight());

		pane.getChildren().add(zoomControlsView.getNode());
	}

	private void updateDistanceLabelPaneVisibility() {
		if (mapRuler.isVisible()) {
			root.setBottom(distanceLabelPane);
		} else {
			root.setBottom(null);
		}
	}

	public List<Node> getToolNodes() {
		List<Node> lst = new ArrayList<>();

		for (Layer layer : layers) {
			List<Node> l = layer.getToolNodes();
			if (!l.isEmpty()) {				
				lst.addAll(l);
			}
		}
		
		return lst;
	}

	private BufferedImage draw(int width, int height) {
		if (width <= 0 || height <= 0) {
			return null;
		}
		
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		Graphics2D g2 = (Graphics2D) bi.getGraphics();
		g2.setPaint(Color.DARK_GRAY);
		g2.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		
		g2.translate(width / 2, height / 2);
		
		
		MapField fixedField = new MapField(model.getMapField());
		
		for (Layer l : layers) {
			try {
				l.draw(g2, fixedField);
			} catch (Exception e) {
				log.error("Error drawing layer", e);
			}
		}
		
		return bi;
	}

	protected void updateUI() {
		if (windowSize.width <= 0 || windowSize.height <= 0) {
			return;
		}

		Platform.runLater(() -> {
			int currentCount = entercount.incrementAndGet();
			if (currentCount > 1) {
				System.err.println("entercount: " + currentCount);
			}
			if (isGpsPresent()) {
				img = draw(windowSize.width, windowSize.height);
			} else {
				img = drawStub();
			}
			toImageView();
			entercount.decrementAndGet();
		});
	}

	public void toImageView() {
		if (img == null) {
			return;
		}
		Image i = SwingFXUtils.toFXImage(img, null);

		imageView.setImage(i);
	}

	public void setSize(int width, int height) {
		windowSize.setSize(width, height);
		setLayerSizes();
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.windowresized));
	}

	public BufferedImage drawStub() {
		BufferedImage noGpsImg = new BufferedImage(windowSize.width, windowSize.height, BufferedImage.TYPE_INT_RGB);
		
		Graphics2D g2 = (Graphics2D)noGpsImg.getGraphics();
		g2.setColor(Color.LIGHT_GRAY);
		g2.fillRect(0, 0, windowSize.width, windowSize.height);
		
		g2.setColor(Color.DARK_GRAY);
		
		FontMetrics fm = g2.getFontMetrics();
		Rectangle2D rect = fm.getStringBounds(NO_GPS_TEXT, g2);
		
		int x = (int) ((windowSize.width - rect.getWidth()) / 2);
		int y = windowSize.height / 2;
		
		g2.drawString(NO_GPS_TEXT, x, y);
		
		return noGpsImg;
	}

	private Node createFixedWidthSpacer() {
		Region spacer = new Region();
		spacer.setPrefWidth(7);
		return spacer;
	}

	private Node createFlexibleSpacer() {
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		return spacer;
	}

	private Point2D getLocalCoords(MouseEvent event) {
		return getLocalCoords(event.getSceneX(), event.getSceneY());
	}

	private Point2D getLocalCoords(double x, double y) {
		Point2D sceneCoords  = new Point2D(x, y);
		Point2D imgCoord = imageView.sceneToLocal(sceneCoords);
		Point2D p = new Point2D(
				imgCoord.getX() - imageView.getBoundsInLocal().getWidth() / 2,
				imgCoord.getY() - imageView.getBoundsInLocal().getHeight() / 2);
		return p;
	}
}

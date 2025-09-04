package com.ugcs.gprvisualizer.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.draw.BaseLayer;
import com.ugcs.gprvisualizer.draw.GpsTrack;
import com.ugcs.gprvisualizer.draw.GridLayer;
import com.ugcs.gprvisualizer.draw.Layer;
import com.ugcs.gprvisualizer.draw.QualityLayer;
import com.ugcs.gprvisualizer.draw.RadarMap;
import com.ugcs.gprvisualizer.draw.RepaintListener;
import com.ugcs.gprvisualizer.draw.SatelliteMap;
import com.ugcs.gprvisualizer.event.WhatChanged;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.FoundTracesLayer;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import com.ugcs.gprvisualizer.event.FileOpenedEvent;

import javax.annotation.Nullable;

@Component
public class MapView implements InitializingBean {
	
	@Autowired
	private TraceCutter traceCutter;

	@Autowired
	private MapRuler mapRuler;
	
	@Autowired
	private Model model;
	
	//@Autowired
	//private Status status;

	@Autowired
	private ApplicationEventPublisher eventPublisher;
	
	@Autowired
	private SatelliteMap satelliteMap;

	@Autowired
	private RadarMap radarMap;

	@Autowired
	private GpsTrack gpsTrackMap;
	
	//@Autowired
	private Dimension wndSize = new Dimension();

	private GridLayer gridLayer;

	@Autowired
	private QualityLayer qualityLayer;

	@Autowired
	private List<BaseLayer> baseLayers;

	private List<Layer> layers = new ArrayList<>();

	public List<Layer> getLayers() {
		return layers;
	}

	public QualityLayer getQualityLayer() {
		return qualityLayer;
	}

	protected ImageView imageView = new ImageView();
	protected BufferedImage img;


	private ToolBar toolBar = new ToolBar();
	private Dimension windowSize = new Dimension();

	private final BorderPane root = new BorderPane();
	@Nullable private DistanceLabelPane distanceLabelPane;

	protected RepaintListener listener = this::updateUI;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		
		radarMap.setRepaintListener(listener);
		
		satelliteMap.setRepaintListener(listener);
		
		gpsTrackMap.setRepaintListener(listener);

		gridLayer.setRepaintListener(listener);

		qualityLayer.setRepaintListener(listener);

		getLayers().add(satelliteMap);
		getLayers().add(radarMap);
		getLayers().add(gridLayer);
		getLayers().add(qualityLayer);
		getLayers().add(gpsTrackMap);
		getLayers().add(new FoundTracesLayer(model));

		//TODO: bad style
		traceCutter.setListener(listener);		
		getLayers().add(traceCutter);

		mapRuler.setRepaintCallback(() -> listener.repaint());
		getLayers().add(mapRuler);

		initImageView();
		
	}
	
	@EventListener
	private void somethingChanged(WhatChanged changed) {
		if (!isGpsPresent()) {
			return;
		}

		if (changed.isJustdraw()) {
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
			
	    	int zoom = model.getMapField().getZoom() + (event.getDeltaY() > 0 ? 1 : -1);
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
	
	protected EventHandler<MouseEvent> mouseClickHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {
			if (!isGpsPresent()) {
				return;
			}        	
        	
        	if (event.getClickCount() == 2) {
        		Point2D p = getLocalCoords(event);
        		LatLon location = model.getMapField().screenTolatLon(p);
				model.selectNearestTrace(location);
			}
		}
	};

	public Node getCenter() {
		
		toolBar.setDisable(true);
		toolBar.getItems().addAll(traceCutter.getToolNodes2());
		toolBar.getItems().addAll(mapRuler.buildToolNodes());
		toolBar.getItems().add(getSpacer());


				
		toolBar.getItems().addAll(getToolNodes());
		
		Pane sp1 = new Pane();
		
		ChangeListener<Number> sp1SizeListener = (observable, oldValue, newValue) -> {
			this.setSize((int) (sp1.getWidth()), (int) (sp1.getHeight()));
		};
		
		
		Node n1 = imageView;
		sp1.getChildren().add(n1);
		sp1.getChildren().add(toolBar);
		
		sp1.widthProperty().addListener(sp1SizeListener);
		sp1.heightProperty().addListener(sp1SizeListener);

		distanceLabelPane = new DistanceLabelPane(mapRuler, this::updateUI, this::updateDistanceLabelPaneVisibility);
		updateDistanceLabelPaneVisibility();
		root.setCenter(sp1);
		
		return root;
	}

	private void updateDistanceLabelPaneVisibility() {
		if (mapRuler.isVisible()) {
			root.setBottom(distanceLabelPane);
		} else {
			root.setBottom(null);
		}
	}

	public List<Node> getRight(SgyFile dataFile) {
		return radarMap.getControlNodes(dataFile);
	}
		
	public List<Node> getToolNodes() {
		
		List<Node> lst = new ArrayList<>();
		
		for (Layer layer : getLayers()) {
			List<Node> l = layer.getToolNodes();
			if (!l.isEmpty()) {				
				lst.addAll(l);
			}
		}
		
		return lst;
	}

	
	private static final String NO_GPS_TEXT = "There are no coordinates in files";
	
	
	protected BufferedImage draw(int width,	int height) {
		if (width <= 0 || height <= 0) {
			return null;
		}
		
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		
		Graphics2D g2 = (Graphics2D) bi.getGraphics();
		g2.setPaint(Color.DARK_GRAY);
		g2.fillRect(0, 0, bi.getWidth(), bi.getHeight());
		
		g2.translate(width / 2, height / 2);
		
		
		MapField fixedField = new MapField(model.getMapField());
		
		for (Layer l : getLayers()) {
			try {
				l.draw(g2, fixedField);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return bi;
	}

	private static AtomicInteger entercount = new AtomicInteger(0);

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

	protected void updateWindow() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				toImageView();
			}
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
		wndSize.setSize(width, height);
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

	private Region getSpacer() {
		Region r3 = new Region();
		r3.setPrefWidth(7);
		return r3;
	}

	protected EventHandler<MouseEvent> mousePressHandler = new EventHandler<MouseEvent>() {
		@Override
		public void handle(MouseEvent event) {
			if (!isGpsPresent()) {
				return;
			}

			Point2D p = getLocalCoords(event);

			for (int i = getLayers().size() - 1; i >= 0; i--) {
				Layer layer = getLayers().get(i);
				try {
					if (layer.mousePressed(p)) {
						return;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	};

	private EventHandler<MouseEvent> mouseReleaseHandler = new EventHandler<MouseEvent>() {
		@Override
		public void handle(MouseEvent event) {
			if (!isGpsPresent()) {
				return;
			}
			Point2D p = getLocalCoords(event);
			for (int i = getLayers().size() - 1; i >= 0; i--) {
				Layer layer = getLayers().get(i);
				if (layer.mouseRelease(p)) {
					return;
				}
			}
		}
	};

	private EventHandler<MouseEvent> mouseMoveHandler = new EventHandler<MouseEvent>() {
		@Override
		public void handle(MouseEvent event) {
			if (!isGpsPresent()) {
				return;
			}
			Point2D p = getLocalCoords(event);
			for (int i = getLayers().size() - 1; i >= 0; i--) {
				Layer layer = getLayers().get(i);
				if (layer.mouseMove(p)) {
					return;
				}
			}
		}
	};

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

	public Dimension getWndSize() {
		return wndSize;
	}
}

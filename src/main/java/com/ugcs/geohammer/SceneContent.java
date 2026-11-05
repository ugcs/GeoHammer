package com.ugcs.geohammer;

import com.ugcs.geohammer.map.MapView;
import com.ugcs.geohammer.chart.tool.OptionPane;
import com.ugcs.geohammer.model.event.ThemeSelectedEvent;
import com.ugcs.geohammer.view.style.Theme;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Rectangle;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class SceneContent extends BorderPane implements InitializingBean {

	@Autowired
	private Loader loader;

	@Autowired
	private StatusBar statusBar;

	@Autowired
	private MapView mapView;

	@Autowired
	private ProfileView profileView;
	
	@Autowired
	private OptionPane optionPane;

	@Autowired
	private ThemeService themeService;

	private BorderPane mapPane;

	private BorderPane chartPane;

	private BorderPane toolPane;
	
	@Override
	public void afterPropertiesSet() throws Exception {
		this.setOnDragOver(loader.getDragHandler());
		this.setOnDragDropped(loader.getDropHandler());
		this.setCenter(createSplitPane());
		this.setBottom(statusBar);
	}

	private SplitPane createSplitPane() {
		// map
		mapPane = createPane(mapView.getCenter());
		mapPane.setMinWidth(100);
		mapPane.setPrefHeight(350);

		// charts
		chartPane = createPane(profileView.getCenter());
		chartPane.setMinWidth(200);

		// tools
		toolPane = createPane(optionPane);
		toolPane.setMinWidth(200);
		toolPane.setMaxWidth(350);
		toolPane.setPrefHeight(350);

		SplitPane splitPane = new SplitPane(mapPane, chartPane, toolPane);
		splitPane.setDividerPositions(0.2, 0.6, 0.2);

		return splitPane;
	}

	private BorderPane createPane(Node content) {
		BorderPane pane = new BorderPane(content);
		setTheme(pane, themeService.getTheme());
		return pane;
	}

	@EventListener
	private void themeSelected(ThemeSelectedEvent event) {
		Theme theme = event.getTheme();

		setTheme(mapPane, theme);
		setTheme(chartPane, theme);
		setTheme(toolPane, theme);
	}

	private void setTheme(BorderPane pane, Theme theme) {
		if (pane == null) {
			return;
		}
		if (theme == Theme.BASIC) {
			pane.setClip(null);
		} else {
			Rectangle clip = new Rectangle();
			clip.setArcWidth(16);
			clip.setArcHeight(16);
			clip.widthProperty().bind(pane.widthProperty());
			clip.heightProperty().bind(pane.heightProperty());
			pane.setClip(clip);
		}
	}
}

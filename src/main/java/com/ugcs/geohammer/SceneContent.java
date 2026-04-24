package com.ugcs.geohammer;

import com.ugcs.geohammer.map.MapView;
import com.ugcs.geohammer.chart.tool.OptionPane;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.FileManager;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.Model;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
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
	private Model model;

	private final Label openHint = createOpenHint();
	
	@Override
	public void afterPropertiesSet() throws Exception {
		this.setOnDragOver(loader.getDragHandler());
		this.setOnDragDropped(loader.getDropHandler());
		this.setCenter(createSplitPane());
		this.setBottom(statusBar);
	}

	private SplitPane createSplitPane() {
		// map
		BorderPane mapPane = createPane(mapView.getCenter());
		mapPane.setMinWidth(100);
		mapPane.setPrefHeight(350);

		// charts
		StackPane profilePane = new StackPane(profileView.getCenter(), openHint);
		BorderPane chartPane = createPane(profilePane);
		chartPane.setMinWidth(200);

		// tools
		BorderPane toolPane = createPane(optionPane);
		toolPane.setMinWidth(200);
		toolPane.setMaxWidth(350);
		toolPane.setPrefHeight(350);

		SplitPane splitPane = new SplitPane(mapPane, chartPane, toolPane);
		splitPane.setDividerPositions(0.2, 0.6, 0.2);

		return splitPane;
	}

	private BorderPane createPane(Node content) {
		BorderPane pane = new BorderPane(content);
		pane.getStyleClass().add("app-pane");
		return pane;
	}

	private Label createOpenHint() {
		Label openHint = new Label("""
			Drag and drop data files here or use \
			the "Open files" button on the toolbar
			""");
		openHint.setTextAlignment(TextAlignment.CENTER);
		openHint.setWrapText(true);
		openHint.setMaxWidth(300);
		openHint.setMouseTransparent(true);
		return openHint;
	}

	private void updateOpenHintVisibility() {
		FileManager fileManager = model.getFileManager();
		int numOpenFiles = fileManager.getFilesCount();
		Platform.runLater(() -> openHint.setVisible(numOpenFiles == 0));
	}

	@EventListener
	private void fileClosed(FileClosedEvent event) {
		updateOpenHintVisibility();
	}

	@EventListener
	private void fileOpened(FileOpenedEvent event) {
		updateOpenHintVisibility();
	}
}

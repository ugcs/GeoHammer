package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.ext.FileManager;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;

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

	private Label openHint = createOpenHint();
	
	@Override
	public void afterPropertiesSet() throws Exception {
		this.setOnDragOver(loader.getDragHandler());
		this.setOnDragDropped(loader.getDropHandler());
		this.setCenter(createSplitPane());
		this.setBottom(statusBar);
	}

	private SplitPane createSplitPane() {
		SplitPane sp = new SplitPane();
		sp.setDividerPositions(0.2f, 0.6f, 0.2f);
		
		//map view
		sp.getItems().add(mapView.getCenter());
		
		//profile view
		StackPane profilePane = new StackPane(profileView.getCenter(), openHint);
		sp.getItems().add(profilePane);
		
		//options tabs
		sp.getItems().add(optionPane);
		
		return sp;
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
		Platform.runLater(() -> {
			openHint.setVisible(numOpenFiles == 0);
		});
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

package com.ugcs.gprvisualizer.app;

import java.awt.Desktop;

import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.ext.FileManager;
import com.ugcs.gprvisualizer.app.scripts.PythonConfig;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
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

	@Autowired
	private PythonConfig pythonConfig;

	private final Label openHint = createOpenHint();
	
	@Override
	public void afterPropertiesSet() throws Exception {
		this.setOnDragOver(loader.getDragHandler());
		this.setOnDragDropped(loader.getDropHandler());
		setupMenuBar();
		this.setCenter(createSplitPane());
		this.setBottom(statusBar);
	}

	private void setupMenuBar() {
		String os = System.getProperty("os.name").toLowerCase();
		if (Desktop.isDesktopSupported() && os.contains("mac")) {
			Desktop desktop = Desktop.getDesktop();
			desktop.setPreferencesHandler(event ->
					Platform.runLater(() ->
							new SettingsView(pythonConfig)
					)
			);
		} else {
			MenuBar menuBar = new MenuBar();
			Menu menu = new Menu("File");
			MenuItem settingsItem = new MenuItem("Settings");
			settingsItem.setOnAction(event -> new SettingsView(pythonConfig));
			menu.getItems().add(settingsItem);
			menuBar.getMenus().add(menu);

			this.setTop(menuBar);
		}
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

package com.ugcs.geohammer.geotagger.view;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.Geotagger;
import com.ugcs.geohammer.geotagger.view.section.FileSectionStrategy;
import com.ugcs.geohammer.model.Model;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FileSectionPanel extends VBox {
	private final ListView<SgyFile> listView;
	private final FileSectionStrategy strategy;
	private final Label headerLabel;
	private final StatusBar statusBar;

	@Autowired
	private Geotagger geotagger;

	@Autowired
	private Model model;

	public FileSectionPanel(String title, FileSectionStrategy strategy, StatusBar statusBar) {
		this.strategy = strategy;
		this.headerLabel = new Label(title);
		this.listView = createListView();
		this.statusBar = statusBar;
		setupUI();
		setupDragAndDrop();
	}

	private ListView<SgyFile> createListView() {
		ListView<SgyFile> listView = new ListView<>();
		listView.setPlaceholder(strategy.createPlaceholder());
		listView.setCellFactory(param -> new FileListCell(strategy));
		VBox.setVgrow(listView, Priority.ALWAYS);
		return listView;
	}

	private void setupUI() {
		headerLabel.setStyle("-fx-font-size: 16px;");

		HBox columnHeaders = strategy.createHeader();
		HBox buttons = createButtonBar();

		columnHeaders.setPadding(new Insets(5, 10, 0, 10));
		getChildren().addAll(headerLabel, columnHeaders, listView, buttons);
	}

	private HBox createButtonBar() {
		Button addButton = new Button("Add File");
		addButton.setOnAction(e -> handleAddFile());

		Button addFolderButton = new Button("Add Folder");
		addFolderButton.setOnAction(e -> handleAddFolder());

		Button removeButton = new Button("Remove");
		removeButton.setOnAction(e -> removeSelected());

		Button clearButton = new Button("Clear");
		clearButton.setOnAction(e -> clear());

		HBox buttonBar = new HBox(8, addButton, addFolderButton, removeButton, clearButton);
		buttonBar.setAlignment(Pos.CENTER_LEFT);
		return buttonBar;
	}

	private void setupDragAndDrop() {
		listView.setOnDragOver(this::handleDragOver);
		listView.setOnDragDropped(this::handleDragDropped);
	}

	private void handleDragOver(DragEvent event) {
		if (event.getGestureSource() != listView && event.getDragboard().hasFiles()) {
			event.acceptTransferModes(TransferMode.COPY);
		}
		event.consume();
	}

	private void handleDragDropped(DragEvent event) {
		Dragboard dragboard = event.getDragboard();
		boolean success = false;

		if (dragboard.hasFiles()) {
			List<File> files = dragboard.getFiles();
			files.forEach(this::addFile);
			success = true;
		}

		event.setDropCompleted(success);
		event.consume();
	}

	private void handleAddFile() {
		strategy.selectAndAddFile(this::addFiles);
	}

	private void handleAddFolder() {
		strategy.selectAndAddFolder(this::addFiles);
	}

	public void addFile(File file) {
		if (file == null || !strategy.isValidFile(file)) {
			return;
		}

		try {
			SgyFile sgyFile = geotagger.createSgyFile(file, model.getFileManager().getFileTemplates());
			if (sgyFile != null && !listView.getItems().contains(sgyFile)) {
				listView.getItems().add(sgyFile);
			}
		} catch (IOException e) {
			statusBar.showMessage("Failed to open file: " + file.getName(), "Error");
		}
	}

	private void addFiles(List<File> files) {
		files.forEach(this::addFile);
	}

	public boolean canAcceptFile(File file) {
		return strategy.isValidFile(file);
	}

	public void removeSelected() {
		int selectedIndex = listView.getSelectionModel().getSelectedIndex();
		if (selectedIndex >= 0) {
			listView.getItems().remove(selectedIndex);
		}
	}

	public void clear() {
		listView.getItems().clear();
	}

	public ObservableList<SgyFile> getFiles() {
		return listView.getItems();
	}
}

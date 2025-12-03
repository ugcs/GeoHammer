package com.ugcs.geohammer.geotagger.view;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.FileTypes;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public abstract class FilePane extends VBox {

    private final Model model;

    private final StatusBar statusBar;

    private final ListView<SgyFile> listView;

    private final Label headerLabel;

	public FilePane(Model model, StatusBar statusBar, String title) {
		this.model = model;
		this.statusBar = statusBar;

		this.headerLabel = new Label(title);
		this.listView = createListView();

		setupUI();
		setupDragAndDrop();
	}

	private void setupUI() {
		headerLabel.setStyle("-fx-font-size: 16px;");

		HBox columnHeaders = createHeader();
		HBox buttons = createButtonBar();

		columnHeaders.setPadding(new Insets(5, 10, 0, 10));
		getChildren().addAll(headerLabel, columnHeaders, listView, buttons);
	}

	private void setupDragAndDrop() {
		listView.setOnDragOver(this::handleDragOver);
		listView.setOnDragDropped(this::handleDragDropped);
	}

	private ListView<SgyFile> createListView() {
		ListView<SgyFile> listView = new ListView<>();
		listView.setPlaceholder(createPlaceholder());
		listView.setCellFactory(param -> new FileListCell());
		VBox.setVgrow(listView, Priority.ALWAYS);
		return listView;
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
		selectAndAddFile(this::addFiles);
	}

	private void handleAddFolder() {
		selectAndAddFolder(this::addFiles);
	}

	private SgyFile openFile(File file, FileTemplates fileTemplates) throws IOException {
		if (FileTypes.isGprFile(file)) {
			GprFile sgyFile = new GprFile();
			sgyFile.open(file);
			return sgyFile;
		} else if (FileTypes.isCsvFile(file)) {
			CsvFile sgyFile = new CsvFile(fileTemplates);
			sgyFile.open(file);
			return sgyFile;
		} else {
			return null;
		}
	}

	public void addFile(File file) {
		if (file == null || !canAdd(file)) {
			return;
		}

		try {
			SgyFile sgyFile = openFile(file, model.getFileManager().getFileTemplates());
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

    protected abstract HBox createHeader();

    protected abstract HBox createDataRow(SgyFile file);

    protected abstract boolean canAdd(File file);

    protected abstract Node createPlaceholder();

    protected abstract void selectAndAddFile(Consumer<List<File>> addFiles);

    protected abstract void selectAndAddFolder(Consumer<List<File>> addFiles);

    class FileListCell extends ListCell<SgyFile> {

        @Override
        protected void updateItem(SgyFile item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(createDataRow(item));
            }
        }
    }
}

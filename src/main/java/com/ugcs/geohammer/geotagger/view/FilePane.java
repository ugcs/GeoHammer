package com.ugcs.geohammer.geotagger.view;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

public abstract class FilePane extends VBox {

	protected static final String HEADER_STYLE = "-fx-background-color: linear-gradient(to bottom, #fafafa, #e5e5e5) ;-fx-padding: 5;";

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
		headerLabel.setStyle("-fx-font-size: 14px;");

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
		removeButton.disableProperty().bind(Bindings.isEmpty(listView.getItems()));

		Button clearButton = new Button("Clear");
		clearButton.setOnAction(e -> clear());
		clearButton.disableProperty().bind(Bindings.isEmpty(listView.getItems()));

		HBox buttonBar = new HBox(8, addButton, addFolderButton, removeButton, clearButton);
		buttonBar.setAlignment(Pos.CENTER_LEFT);
		buttonBar.setPadding(new Insets(5, 0, 0, 0));
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
			addFiles(files);
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

	public void addGeohammerFiles() {
		List<File> files = model.getFileManager().getFiles().stream()
				.filter(sgyFile -> sgyFile != null && sgyFile.getFile() != null)
				.map(SgyFile::getFile)
				.toList();
		addFiles(files);
	}

	private boolean containsFile(File file) {
		for (SgyFile listFile : listView.getItems()) {
			if (Objects.equals(listFile.getFile(), file)) {
				return true;
			}
		}
		return false;
	}

	private SgyFile openFile(File file) throws IOException {
		if (FileTypes.isGprFile(file)) {
			GprFile sgyFile = new GprFile();
			sgyFile.open(file);
			return sgyFile;
		} else if (FileTypes.isCsvFile(file)) {
			CsvFile sgyFile = new CsvFile(model.getFileManager().getFileTemplates());
			sgyFile.open(file);
			return sgyFile;
		} else {
			return null;
		}
	}

	public void addFile(File file) {
		if (file == null) {
			return;
		}
		if (containsFile(file)) {
			return;
		}

		SgyFile sgyFile = model.getFileManager().getFile(file);
		if (sgyFile == null) {
			// open
			try {
				sgyFile = openFile(file);
			} catch (IOException e) {
				statusBar.showMessage("Failed to open file: " + file.getName(), "Error");
			}
		}
		if (sgyFile != null) {
			listView.getItems().add(sgyFile);
		}
	}

	private void addFiles(List<File> files) {
		for (File file : files) {
			if (canAdd(file)) {
				addFile(file);
			}
		}
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

	protected Node createPlaceholder() {
		VBox box = new VBox(5);
		box.setAlignment(Pos.CENTER);
		ImageView uploadImage = ResourceImageHolder.getImageView("upload_file.png");
		if (uploadImage == null) {
			return null;
		}
		uploadImage.setFitHeight(32);
		uploadImage.setFitWidth(32);
		Views.tintImage(uploadImage, Color.GRAY);
		Label label = new Label("Drag and drop files here");
		label.setTextFill(Color.GRAY);
		box.getChildren().addAll(uploadImage, label);
		return box;
	}

	protected void selectAndAddFile(Consumer<List<File>> consumer) {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Files");
		List<File> files = fileChooser.showOpenMultipleDialog(AppContext.stage);
		if (files != null && !files.isEmpty())
			consumer.accept(files);
	}

	protected void selectAndAddFolder(Consumer<List<File>> consumer) {
		DirectoryChooser directoryChooser = new DirectoryChooser();
		directoryChooser.setTitle("Select Folder");
		File dir = directoryChooser.showDialog(AppContext.stage);
		if (dir != null && dir.isDirectory()) {
			File[] files = dir.listFiles(File::isFile);
			if (files != null) {
				consumer.accept(List.of(files));
			}
		}
	}

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

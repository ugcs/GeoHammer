package com.ugcs.geohammer.geotagger;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.FileTypes;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;


@Component
public class GeotaggerView {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

	private static final int FILE_NAME_WIDTH = 350;
	private static final int TEMPLATE_NAME_WIDTH = 100;
	private static final int TIME_WIDTH = 150;
	private static final int COVERAGE_STATUS_WIDTH = 150;
	private static final int HEADER_SPACING = 10;
	private static final int ROOT_SPACING = 12;
	private static final int SECTION_HEIGHT = 200;

	private static final String TITLE = "GeoTagger";
	private static final String POSITION_HEADER = "Position Files";
	private static final String DATA_HEADER = "Data Files";
	private static final String POSITION_PLACEHOLDER = "Drag and drop position files here";
	private static final String DATA_PLACEHOLDER = "Drag and drop data files here";

	private final Model model;
	private final GeoTagger geoTagger;
	private final StatusBar statusBar;
	private final PositionFileChecker positionFileChecker;

	private Stage stage;
	private final Scene scene;

	private final ListView<String> positionFilesView;
	private final ListView<String> dataFilesListView;

	private final List<SgyFile> positionFiles = new ArrayList<>();
	private final List<SgyFile> dataFiles = new ArrayList<>();

	private ProgressBar progressBar;
	private Label progressLabel;
	private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);

	public GeotaggerView(Model model, GeoTagger geoTagger, StatusBar statusBar, PositionFileChecker positionFileChecker) {
		this.model = model;
		this.geoTagger = geoTagger;
		this.statusBar = statusBar;
		this.positionFileChecker = positionFileChecker;

		VBox root = new VBox(ROOT_SPACING);
		root.setPadding(new Insets(12));

		positionFilesView = createFileSection(
				POSITION_HEADER,
				POSITION_PLACEHOLDER,
				Section.POSITION_FILES,
				this::createPositionRow
		);

		dataFilesListView = createFileSection(
				DATA_HEADER,
				DATA_PLACEHOLDER,
				Section.DATA_FILES,
				this::createDataRow
		);

		Node processBtnView = createProcessView();

		root.getChildren().addAll(positionFilesView.getParent(), dataFilesListView.getParent(), processBtnView);

		scene = new Scene(root, 1000, 600);
	}

	public Stage showWindow() {
		if (stage == null) {
			stage = new Stage();
			stage.initModality(Modality.NONE);
			stage.setTitle("GeoTagger");
			stage.setScene(scene);
			stage.setOnCloseRequest(e -> stage = null);
		}
		if (!stage.isShowing()) {
			stage.show();
		}
		stage.toFront();
		stage.requestFocus();

		addAlreadyOpenedFiles();
		return stage;
	}

	private ListView<String> createFileSection(String headerText,
											   String placeholderText,
											   Section section,
											   RowFactory rowFactory) {
		Label header = new Label(headerText);
		header.setStyle("-fx-font-size: 16px;");

		HBox columnHeaders = section == Section.DATA_FILES ? createDataHeader() : createPositionHeader();
		columnHeaders.setPadding(new Insets(5, 10, 0, 10));

		ListView<String> listView = new ListView<>();
		listView.setPlaceholder(GeotaggerViewHelper.createDragAndDropPlaceholder(placeholderText));
		listView.setPrefHeight(SECTION_HEIGHT);
		listView.setStyle("-fx-border-color: #cccccc; -fx-border-style: dashed; -fx-border-width: 2px; -fx-border-radius: 5px;");

		listView.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(String name, boolean empty) {
				super.updateItem(name, empty);
				if (empty || name == null) {
					setGraphic(null);
				} else {
					setGraphic(rowFactory.build(name));
				}
			}
		});

		setupDragAndDrop(listView, section);
		listView.getSelectionModel().clearSelection();

		HBox buttons = createButtonsBox(listView, section);
		VBox sectionBox = new VBox();
		sectionBox.getChildren().addAll(header, columnHeaders, listView, buttons);
		VBox.setMargin(buttons, new Insets(10, 0, 0, 0));

		listView.setUserData(sectionBox);
		return listView;
	}

	private HBox createPositionHeader() {
		HBox header = new HBox(HEADER_SPACING);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getChildren().addAll(
				GeotaggerViewHelper.headerLabel("File", FILE_NAME_WIDTH),
				GeotaggerViewHelper.spacer(),
				GeotaggerViewHelper.headerLabel("Template", TEMPLATE_NAME_WIDTH),
				GeotaggerViewHelper.headerLabel("Start Time", TIME_WIDTH),
				GeotaggerViewHelper.headerLabel("End Time", TIME_WIDTH)
		);
		return header;
	}

	private HBox createDataHeader() {
		HBox header = new HBox(HEADER_SPACING);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getChildren().addAll(
				GeotaggerViewHelper.headerLabel("File", FILE_NAME_WIDTH),
				GeotaggerViewHelper.spacer(),
				GeotaggerViewHelper.headerLabel("Template", TEMPLATE_NAME_WIDTH),
				GeotaggerViewHelper.headerLabel("Start Time", TIME_WIDTH),
				GeotaggerViewHelper.headerLabel("End Time", TIME_WIDTH),
				GeotaggerViewHelper.headerLabel("Coverage Status", COVERAGE_STATUS_WIDTH)
		);
		return header;
	}

	private HBox createPositionRow(String fileName) {
		RowInfo info = extractCommonInfo(fileName, positionFiles);
		return buildRow(info, false);
	}

	private HBox createDataRow(String fileName) {
		RowInfo info = extractCommonInfo(fileName, dataFiles);
		info.coverageStatus = resolveCoverage(fileName);
		return buildRow(info, true);
	}

	private HBox buildRow(RowInfo info, boolean includeCoverage) {
		Label fileNameLabel = GeotaggerViewHelper.fixedLabel(info.fileName, FILE_NAME_WIDTH);
		Label templateLabel = GeotaggerViewHelper.fixedLabel(info.templateName, TEMPLATE_NAME_WIDTH);
		Label startLabel = GeotaggerViewHelper.fixedLabel(info.startTime, TIME_WIDTH);
		Label endLabel = GeotaggerViewHelper.fixedLabel(info.endTime, TIME_WIDTH);

		HBox row = new HBox(HEADER_SPACING);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getChildren().addAll(fileNameLabel, GeotaggerViewHelper.spacer(), templateLabel, startLabel, endLabel);
		if (includeCoverage) {
			row.getChildren().add(GeotaggerViewHelper.fixedLabel(info.coverageStatus, COVERAGE_STATUS_WIDTH));
		}
		return row;
	}

	private RowInfo extractCommonInfo(String fileName, List<SgyFile> source) {
		RowInfo info = new RowInfo();
		info.fileName = fileName;
		SgyFile sgy = findSgy(source, fileName);
		if (sgy == null || sgy.getFile() == null) return info;

		List<GeoData> geoData = sgy.getGeoData();
		if (geoData == null || geoData.isEmpty()) return info;

		Template template = model.getFileManager().getFileTemplates().findTemplate(
				model.getFileManager().getFileTemplates().getTemplates(), sgy.getFile()
		);
		if (template != null) {
			info.templateName = template.getName();
		}

		GeoData first = geoData.getFirst();
		GeoData last = geoData.getLast();
		if (first.getDateTime() != null) info.startTime = first.getDateTime().format(FORMATTER);
		if (last.getDateTime() != null) info.endTime = last.getDateTime().format(FORMATTER);
		return info;
	}

	private String resolveCoverage(String fileName) {
		SgyFile data = findSgy(dataFiles, fileName);
		if (data == null) return "-";
		CoverageStatus status = CoverageStatusResolver.resolve(positionFiles, data);
		return status != null ? status.getDisplayName() : "-";
	}

	private SgyFile findSgy(List<SgyFile> list, String name) {
		for (SgyFile f : list) {
			if (f != null && f.getFile() != null && f.getFile().getName().equals(name)) {
				return f;
			}
		}
		return null;
	}

	private Node createProcessView() {
		Button cancelButton = new Button("Cancel");
		Button processButton = new Button("Process");
		processButton.setStyle("-fx-background-color: #007AFF; -fx-text-fill: white;");

		processButton.disableProperty().bind(
				Bindings.or(
						Bindings.or(
								Bindings.isEmpty(positionFilesView.getItems()),
								Bindings.isEmpty(dataFilesListView.getItems())
						),
						isProcessing
				)
		);

		progressBar = new ProgressBar();
		progressBar.setPrefWidth(300);
		progressBar.setVisible(false);
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

		progressLabel = new Label();
		progressLabel.setVisible(false);

		cancelButton.setOnAction(e -> {
			Stage currentStage = (Stage) cancelButton.getScene().getWindow();
			currentStage.close();
		});

		processButton.setOnAction(e -> startProcessing());

		HBox progressBox = new HBox(10, progressBar, progressLabel);
		progressBox.setAlignment(Pos.CENTER_LEFT);

		HBox buttonBox = new HBox(10, cancelButton, processButton);
		buttonBox.setAlignment(Pos.CENTER_RIGHT);

		Region spacer = GeotaggerViewHelper.spacer();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		return new HBox(10, progressBox, spacer, buttonBox);
	}

	private void startProcessing() {
		isProcessing.set(true);
		progressBar.setVisible(true);
		progressLabel.setVisible(true);
		progressLabel.setText("Processing...");

		List<SgyFile> pos = positionFiles.stream().filter(Objects::nonNull).toList();
		List<SgyFile> data = dataFiles.stream().filter(Objects::nonNull).toList();

		CompletableFuture<String> future = geoTagger.updateCoordinates(pos, data, pct -> Platform.runLater(() -> {
			double progress = Math.clamp(pct, 0, 100) / 100.0;
			progressBar.setProgress(progress);
			progressLabel.setText("Progress: " + pct + "%");
		}));

		future.thenAccept(msg -> Platform.runLater(() -> finishProcess(msg)))
				.exceptionally(ex -> {
					Platform.runLater(() -> finishProcess(ex.getMessage()));
					return null;
				});
	}

	private void finishProcess(String message) {
		progressBar.setVisible(false);
		progressLabel.setVisible(false);
		isProcessing.set(false);
		statusBar.showMessage(message, TITLE);
	}

	private void setupDragAndDrop(ListView<String> listView, Section section) {
		listView.setOnDragOver(event -> {
			if (event.getGestureSource() != listView && event.getDragboard().hasFiles()) {
				event.acceptTransferModes(TransferMode.COPY);
			}
			event.consume();
		});

		listView.setOnDragDropped(event -> {
			Dragboard db = event.getDragboard();
			boolean success = false;
			if (db.hasFiles()) {
				db.getFiles().forEach(f -> addFileSafe(f, listView, section));
				success = true;
			}
			event.setDropCompleted(success);
			event.consume();
		});
	}

	private HBox createButtonsBox(ListView<String> listView, Section section) {
		Button addButton = new Button("Add");
		Button removeButton = new Button("Remove");
		Button addFolderButton = new Button("Add Folder");
		Button clearButton = new Button("Clear");

		addButton.setOnAction(e -> chooseFiles(
				getOwnerStage(),
				section == Section.POSITION_FILES ? "Select Position Files" : "Select Data Files",
				files -> files.forEach(f -> addFileSafe(f, listView, section))
		));

		addFolderButton.setOnAction(e -> chooseFolder(getOwnerStage(),
				dirFiles -> dirFiles.forEach(f -> addFileSafe(f, listView, section))));

		removeButton.setOnAction(e -> removeSelected(listView, section));

		clearButton.setOnAction(e -> {
			listView.getItems().clear();
			(section == Section.POSITION_FILES ? positionFiles : dataFiles).clear();
		});

		HBox box = new HBox(8, addButton, addFolderButton, removeButton, clearButton);
		box.setAlignment(Pos.CENTER_LEFT);
		return box;
	}

	private void chooseFiles(Stage owner, String title, Consumer<List<File>> consumer) {
		FileChooser fc = new FileChooser();
		fc.setTitle(title);
		List<File> files = fc.showOpenMultipleDialog(owner);
		if (files != null && !files.isEmpty())
			consumer.accept(files);
	}

	private void chooseFolder(Stage owner, Consumer<List<File>> consumer) {
		DirectoryChooser dc = new DirectoryChooser();
		dc.setTitle("Select Folder");
		File dir = dc.showDialog(owner);
		if (dir != null && dir.isDirectory()) {
			File[] files = dir.listFiles(File::isFile);
			if (files != null)
				consumer.accept(List.of(files));
		}
	}

	private void removeSelected(ListView<String> listView, Section section) {
		int idx = listView.getSelectionModel().getSelectedIndex();
		if (idx >= 0) {
			listView.getItems().remove(idx);
			List<SgyFile> target = section == Section.POSITION_FILES ? positionFiles : dataFiles;
			if (idx < target.size()) target.remove(idx);
		}
	}

	private void addFileSafe(File file, ListView<String> listView, Section section) {
		Predicate<File> validator = section == Section.POSITION_FILES ? this::isPositionFile : this::isDataFile;
		List<SgyFile> target = section == Section.POSITION_FILES ? positionFiles : dataFiles;
		if (!validator.test(file)) return;
		try {
			SgyFile sgy = geoTagger.toSgyFile(file);
			listView.getItems().add(file.getName());
			target.add(sgy);
		} catch (Exception e) {
			statusBar.showMessage("Failed to open file: " + e.getMessage(), TITLE);
		}
	}

	private void addAlreadyOpenedFiles() {
		model.getFileManager().getFiles().stream()
				.filter(f -> f != null && f.getFile() != null)
				.map(SgyFile::getFile)
				.forEach(file -> {
					if (positionFiles.stream().anyMatch(f -> f.getFile() != null && f.getFile().equals(file))
							|| dataFiles.stream().anyMatch(f -> f.getFile() != null && f.getFile().equals(file))) {
						return;
					}
					if (isPositionFile(file)) {
						addFileSafe(file, positionFilesView, Section.POSITION_FILES);
					} else if (isDataFile(file)) {
						addFileSafe(file, dataFilesListView, Section.DATA_FILES);
					}
				});
	}

	private boolean isPositionFile(File file) {
		return positionFileChecker.isPositionFile(file);
	}

	private boolean isDataFile(File file) {
		return FileTypes.isGprFile(file) || FileTypes.isCsvFile(file);
	}

	private Stage getOwnerStage() {
		return (stage != null) ? stage : new Stage();
	}

	private static final class RowInfo {
		String fileName = "-";
		String templateName = "-";
		String startTime = "-";
		String endTime = "-";
		String coverageStatus = "-";
	}

	private enum Section { POSITION_FILES, DATA_FILES }

	@FunctionalInterface
	private interface RowFactory {
		HBox build(String fileName);
	}
}

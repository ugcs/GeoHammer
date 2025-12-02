
package com.ugcs.geohammer.geotagger.view;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.Geotagger;
import com.ugcs.geohammer.geotagger.PositionSourceFileIdentifier;
import com.ugcs.geohammer.geotagger.SgyFileInfoExtractor;
import com.ugcs.geohammer.geotagger.view.section.DataFileSectionStrategy;
import com.ugcs.geohammer.geotagger.view.section.PositionFileSectionStrategy;
import com.ugcs.geohammer.model.Model;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;


@Component
public class GeotaggerView {

	private static final String TITLE = "GeoTagger";

	private final Model model;
	private final StatusBar statusBar;

	private final FileSectionPanel positionPanel;
	private final FileSectionPanel dataPanel;
	private final ProcessingController processingController;
	private final ProcessView processView;
	private Stage stage;
	private final Scene scene;

	public GeotaggerView(Model model, StatusBar statusBar,
						 Geotagger geotagger,
						 PositionSourceFileIdentifier positionSourceFileIdentifier,
						 ProcessingController processingController,
						 SgyFileInfoExtractor fileInfoExtractor) {
		this.model = model;
		this.statusBar = statusBar;
		this.processingController = processingController;

		VBox root = new VBox(12);
		root.setPadding(new Insets(12));

		positionPanel = new FileSectionPanel(
				model,
				geotagger,
				"Position Files",
				new PositionFileSectionStrategy(
						this::getOwnerStage,
						positionSourceFileIdentifier,
						fileInfoExtractor
				),
				statusBar
		);

		dataPanel = new FileSectionPanel(
				model,
				geotagger,
				"Data Files",
				new DataFileSectionStrategy(this::getOwnerStage, fileInfoExtractor, positionPanel),
				statusBar
		);

		BooleanBinding cannotProcess = Bindings.or(
				Bindings.isEmpty(positionPanel.getFiles()),
				Bindings.isEmpty(dataPanel.getFiles())
		);

		processView = new ProcessView(
				cannotProcess,
				this::closeWindow,
				this::startProcessing
		);

		root.getChildren().addAll(positionPanel, dataPanel, processView);

		scene = new Scene(root, 1000, 600);
	}

	public Stage showWindow() {
		if (stage == null) {
			stage = new Stage();
			stage.initModality(Modality.NONE);
			stage.setTitle("GeoTagger");
			stage.setScene(scene);
			stage.setOnCloseRequest(event -> stage = null);
		}
		if (!stage.isShowing()) {
			stage.show();
		}
		stage.toFront();
		stage.requestFocus();

		addAlreadyOpenedFiles();
		return stage;
	}

	private void startProcessing() {
		processView.startProcessing();

		List<SgyFile> pos = positionPanel.getFiles()
				.stream()
				.filter(Objects::nonNull)
				.toList();
		List<SgyFile> data = dataPanel.getFiles()
				.stream()
				.filter(Objects::nonNull)
				.toList();

		processingController.startProcessing(
				pos,
				data,
				processView::updateProgress,
				this::finishProcess
		);
	}

	private void finishProcess(String message) {
		processView.finishProcessing();
		statusBar.showMessage(message, TITLE);
	}

	private void closeWindow() {
		if (stage != null) {
			stage.close();
			stage = null;
		}
	}

	private void addAlreadyOpenedFiles() {
		Set<File> existingFiles = Stream.concat(
						positionPanel.getFiles().stream(),
						dataPanel.getFiles().stream()
				)
				.map(SgyFile::getFile)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		model.getFileManager().getFiles().stream()
				.filter(sgyFile -> sgyFile != null && sgyFile.getFile() != null)
				.map(SgyFile::getFile)
				.filter(file -> !existingFiles.contains(file))
				.forEach(file -> {
					if (positionPanel.canAcceptFile(file)) {
						positionPanel.addFile(file);
					} else if (dataPanel.canAcceptFile(file)) {
						dataPanel.addFile(file);
					}
				});
	}

	private Window getOwnerStage() {
		return stage;
	}
}

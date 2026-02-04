
package com.ugcs.geohammer.geotagger.view;

import java.util.concurrent.ExecutorService;

import com.ugcs.geohammer.StatusBar;
import com.ugcs.geohammer.geotagger.Geotagger;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.domain.CoverageStatus;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class GeotaggerView {

	private static final String TITLE = "Geotagging";
	private static final Logger log = LoggerFactory.getLogger(GeotaggerView.class);

	private final Model model;

	private final Geotagger geotagger;

	private final StatusBar statusBar;

	private final ExecutorService executor;

	private Stage stage;

	private final Scene scene;

	// view

	private final PositionFilePane positionPane;

	private final DataFilePane dataPane;

	private final ProgressBar progressBar;

	private final Label progressLabel;

	private final Button closeButton;

	private final Button processButton;

	public GeotaggerView(
			Model model,
			Geotagger geotagger,
			StatusBar statusBar,
			ExecutorService executor,
			PositionFilePane positionPane,
			DataFilePane dataPane
	) {
		this.model = model;
		this.geotagger = geotagger;
		this.statusBar = statusBar;
		this.executor = executor;
		this.positionPane = positionPane;
		this.dataPane = dataPane;
		this.dataPane.setCoverageStatusFunction(
				file -> CoverageStatus.compute(file, positionPane.getFiles()));
		this.positionPane.getFiles().addListener((ListChangeListener<SgyFile>) change ->
				dataPane.refresh());

		progressBar = new ProgressBar();
		progressBar.setPrefWidth(300);
		progressBar.setVisible(false);
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

		progressLabel = new Label();
		progressLabel.setVisible(false);

		closeButton = new Button("Close");
		closeButton.setOnAction(event -> closeWindow());

		processButton = new Button("Process");
		processButton.setStyle("-fx-background-color: #007AFF; -fx-text-fill: white;");
		processButton.setOnAction(event -> processFiles());

		processButton.disableProperty().bind(
				Bindings.or(
						Bindings.isEmpty(positionPane.getFiles()),
						Bindings.isEmpty(dataPane.getFiles())
								.or(progressBar.visibleProperty())));

		HBox progressBox = new HBox(10, progressBar, progressLabel);
		progressBox.setAlignment(Pos.CENTER_LEFT);

		HBox buttonBox = new HBox(10, closeButton, processButton);
		buttonBox.setAlignment(Pos.CENTER_RIGHT);

		Region spacer = Views.createSpacer();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox controlPane = new HBox(10);
		controlPane.getChildren().addAll(progressBox, spacer, buttonBox);

		VBox root = new VBox(12);
		root.setPadding(new Insets(12));
		root.getChildren().addAll(positionPane, dataPane, controlPane);

		scene = new Scene(root, 1000, 600);
	}

	public Stage showWindow() {
		if (stage == null) {
			stage = new Stage();
            stage.initModality(Modality.NONE);
            stage.setTitle(TITLE);
            stage.setScene(scene);
            stage.setOnHiding(event -> closeWindow());
            stage.setOnCloseRequest(event -> closeWindow());
		}
		if (!stage.isShowing()) {
			stage.show();
		}
		stage.toFront();
		stage.requestFocus();

		positionPane.addGeohammerFiles();
		dataPane.addGeohammerFiles();
		return stage;
	}

	private void closeWindow() {
		positionPane.clear();
		dataPane.clear();
		if (stage != null) {
			stage.close();
			stage = null;
		}
	}

	private void showProgress() {
		progressBar.setVisible(true);
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
		progressLabel.setVisible(true);
		progressLabel.setText("Processing...");
	}

	public void hideProgress() {
		progressBar.setVisible(false);
		progressLabel.setVisible(false);
	}

	public void updateProgress(int progress, int maxProgress) {
		double completion = maxProgress > 0 ? (double) progress / maxProgress : 0.0;
		int percentage = (int) (completion * 100);
		Platform.runLater(() -> {
			progressBar.setProgress(completion);
			progressLabel.setText("Progress: " + percentage + "%");
		});
	}

	private void processFiles() {
		showProgress();
		executor.submit(() -> {
			try {
				geotagger.interpolateAndUpdatePositions(
						dataPane.getFiles(),
						positionPane.getFiles(),
						this::updateProgress);
				statusBar.showMessage("Processing finished successfully", TITLE);
			} catch (Exception e) {
				log.warn("Processing failed", e);
				String message = "Processing failed: " + e.getMessage();
				statusBar.showMessage(message, TITLE);
			} finally {
				Platform.runLater(this::hideProgress);
			}
		});
	}
}

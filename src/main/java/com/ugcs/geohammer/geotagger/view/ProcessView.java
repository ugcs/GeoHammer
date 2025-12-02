package com.ugcs.geohammer.geotagger.view;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class ProcessView extends HBox {

	private final ProgressBar progressBar;
	private final Label progressLabel;
	private final Button processButton;
	private final BooleanProperty isProcessing = new SimpleBooleanProperty(false);

	public ProcessView(BooleanBinding disableProcessing, Runnable onClose, Runnable onProcess) {
		super(10);

		Button closeButton = new Button("Close");
		processButton = new Button("Process");
		processButton.setStyle("-fx-background-color: #007AFF; -fx-text-fill: white;");

		processButton.disableProperty().bind(disableProcessing.or(isProcessing));

		progressBar = new ProgressBar();
		progressBar.setPrefWidth(300);
		progressBar.setVisible(false);
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

		progressLabel = new Label();
		progressLabel.setVisible(false);

		closeButton.setOnAction(event -> onClose.run());
		processButton.setOnAction(event -> onProcess.run());

		HBox progressBox = new HBox(10, progressBar, progressLabel);
		progressBox.setAlignment(Pos.CENTER_LEFT);

		HBox buttonBox = new HBox(10, closeButton, processButton);
		buttonBox.setAlignment(Pos.CENTER_RIGHT);

		Region spacer = GeotaggerComponents.spacer();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		this.getChildren().addAll(progressBox, spacer, buttonBox);
	}

	public void startProcessing() {
		isProcessing.set(true);
		progressBar.setVisible(true);
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
		progressLabel.setVisible(true);
		progressLabel.setText("Processing...");
	}

	public void updateProgress(double percentage) {
		progressBar.setProgress(percentage / 100.0);
		progressLabel.setText("Progress: " + (int) percentage + "%");
	}

	public void finishProcessing() {
		isProcessing.set(false);
		progressBar.setVisible(false);
		progressLabel.setVisible(false);
	}

	public boolean isProcessing() {
		return isProcessing.get();
	}
}

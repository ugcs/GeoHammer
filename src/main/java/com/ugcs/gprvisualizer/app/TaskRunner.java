package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.app.intf.Status;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class TaskRunner implements ProgressListener {

	private Status status;
	private Stage primaryStage = AppContext.stage;
	private Stage dialog = new Stage();
	private ImageView loadingView = ResourceImageHolder.getImageView("loading.gif");
	private ProgressTask task;

	private VBox dialogVbox = new VBox(20);

	private Button closeButton = new Button("Close");

	private String message = "";

	public TaskRunner(Status status, ProgressTask task) {
		this.status = status;

		this.task = task;


		closeButton.setOnAction(e -> {
			closePopup();
		});

		//new Image(this.getClass().getResource("java.gif").toExternalForm());

		dialog.initStyle(StageStyle.UNDECORATED);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(primaryStage);


        dialogVbox.getChildren().addAll(loadingView);

	}

	public void start() {
		start(true);
	}

	public void start(boolean withWaitCursor) {
		if (withWaitCursor) {
			showPopup();
		}

		try {
			task.run(TaskRunner.this);
		} catch (Exception e) {
			e.printStackTrace();
			progressMsg("Error: " + e.getMessage());
		} finally {
			if (withWaitCursor) {
				closePopup();
			}
		}
	}

	protected void closePopup() {
		Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
	}

	protected void showPopup() {
		Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.WAIT));
	}

	@Override
	public void progressMsg(String msg) {
		this.message = msg;

		show(message);
	}

	protected void show(String msg) {
		Platform.runLater(() -> status.showMessage(msg, "Task"));
	}

	@Override
	public void progressPercent(int percent) {

	}

	@Override
	public void progressSubMsg(String msg) {
		show(message + " " + msg);

	}

}

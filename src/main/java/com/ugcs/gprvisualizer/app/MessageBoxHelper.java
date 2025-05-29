package com.ugcs.gprvisualizer.app;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;

public class MessageBoxHelper {

	private static final double CONTENT_WIDTH = 400.0;

	public static void showError(String header, String msg) {
		
		System.out.println("MessageBoxHelper.showError " + header + " " + msg);
		
		Platform.runLater(() -> {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error");
			alert.setHeaderText(header);

			Label content = new Label(msg);
			content.setWrapText(true);
			content.setPrefWidth(CONTENT_WIDTH);
			content.setMaxWidth(CONTENT_WIDTH);

			alert.getDialogPane().setContent(content);
			alert.showAndWait();
		});
	}
}

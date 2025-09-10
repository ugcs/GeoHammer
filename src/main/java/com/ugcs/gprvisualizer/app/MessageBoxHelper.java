package com.ugcs.gprvisualizer.app;

import com.ugcs.gprvisualizer.utils.Strings;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageBoxHelper {

	private static final Logger log = LoggerFactory.getLogger(MessageBoxHelper.class);

	private static final double CONTENT_WIDTH = 400.0;

	private static void showAlert(AlertType alertType, String header, String message) {
		log.info("MessageBox {}: [{}] {}", alertType, header, message);

		Platform.runLater(() -> {
			Alert alert = new Alert(alertType);

			String title = alertType.toString();
			if (!Strings.isNullOrEmpty(title)) {
				title = title.substring(0, 1).toUpperCase()
						+ title.substring(1).toLowerCase();
			}

			alert.setTitle(title);
			alert.setHeaderText(header);

			Label content = new Label(message);
			content.setWrapText(true);
			content.setPrefWidth(CONTENT_WIDTH);
			content.setMaxWidth(CONTENT_WIDTH);

			alert.getDialogPane().setContent(content);
			alert.showAndWait();
		});
	}


	public static void showError(String header, String message) {
		showAlert(AlertType.ERROR, header, message);
	}

	public static void showInformation(String header, String message) {
		showAlert(AlertType.INFORMATION, header, message);
	}
}

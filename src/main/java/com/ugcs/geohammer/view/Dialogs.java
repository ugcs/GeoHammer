package com.ugcs.geohammer.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.feedback.FeedbackView;
import com.ugcs.geohammer.util.Strings;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Dialogs {

	private static final String DEFAULT_ALERT_TITLE = "Alert";

	private static final double CONTENT_WIDTH = 400.0;

	public static final ButtonType REPORT_ISSUE = new ButtonType("Report issue", ButtonBar.ButtonData.OK_DONE);

	public static final ButtonType SUBMIT_FEEDBACK = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);

	private static Alert createAlert(AlertType alertType, String header, String message) {
		if (alertType == null) {
			alertType = AlertType.NONE;
		}

		Alert alert = new Alert(alertType);
		alert.setTitle(getAlertTitle(alertType));
		alert.setHeaderText(header);

		Label content = new Label(message);
		content.setWrapText(true);
		content.setPrefWidth(CONTENT_WIDTH);
		content.setMaxWidth(CONTENT_WIDTH);

		alert.getDialogPane().setContent(content);
		alert.initOwner(AppContext.stage);

		return alert;
	}

	private static String getAlertTitle(AlertType alertType) {
		if (alertType == null || alertType == AlertType.NONE) {
			return DEFAULT_ALERT_TITLE;
		}
		String title = alertType.toString();
		if (!Strings.isNullOrEmpty(title)) {
			title = title.substring(0, 1).toUpperCase()
					+ title.substring(1).toLowerCase();
		}
		return title;
	}

	public static void showInformation(String header, String message) {
		Platform.runLater(() -> {
			Alert alert = createAlert(AlertType.INFORMATION, header, message);
			alert.show();
		});
	}

	public static void showError(String header, String message) {
		showError(header, message, null);
	}

	public static void showError(String header, String message, Throwable t) {
		Platform.runLater(() -> {
			FeedbackView feedback = new FeedbackView(header, getFeedbackMessage(message, t));
			feedback.setPrefSize(380, 300);

            VBox expandable = new VBox(8,
					new Separator(),
					feedback);
			expandable.setPadding(new Insets(16, 12, 8, 12));
			VBox.setVgrow(feedback, Priority.ALWAYS);

			Alert alert = createAlert(AlertType.ERROR, header, message);
			DialogPane dialogPane = alert.getDialogPane();
			dialogPane.setExpandableContent(expandable);
			dialogPane.setExpanded(true);

			alert.getButtonTypes().setAll(REPORT_ISSUE, ButtonType.CLOSE);
			Button reportIssue = (Button)dialogPane.lookupButton(REPORT_ISSUE);
			reportIssue.addEventFilter(ActionEvent.ACTION, event -> {
				if (!feedback.validate()) {
					event.consume();
					return;
				}
				feedback.submit();
			});

			alert.show();
		});
	}

	private static String getFeedbackMessage(String message, Throwable t) {
		if (t == null) {
			return message;
		}
		StringBuilder feedbackMessage = new StringBuilder();
		if (!Strings.isNullOrEmpty(message)) {
			feedbackMessage.append(message).append("\n\n");
		}
		feedbackMessage.append("--- stack trace ---\n\n")
				.append(stackTraceToString(t));
		return feedbackMessage.toString();
	}

	private static String stackTraceToString(Throwable t) {
		if (t == null) {
			return Strings.empty();
		}
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	public static void showFeedback() {
		Platform.runLater(() -> {
			FeedbackView feedback = new FeedbackView();
			feedback.setPrefSize(380, 300);

			Dialog<Void> dialog = new Dialog<>();
			dialog.setTitle("Feedback");

			DialogPane dialogPane = dialog.getDialogPane();
			Stage stage = (Stage)dialogPane.getScene().getWindow();
			stage.setMinWidth(300);
			stage.setMinHeight(300);
			stage.setResizable(true);

			dialogPane.setContent(feedback);
			dialogPane.getButtonTypes().addAll(SUBMIT_FEEDBACK, ButtonType.CLOSE);

			Button submitFeedback = (Button) dialogPane.lookupButton(SUBMIT_FEEDBACK);
			submitFeedback.addEventFilter(ActionEvent.ACTION, event -> {
				if (!feedback.validate()) {
					event.consume();
					return;
				}
				feedback.submit();
			});

			dialog.show();
		});
	}
}

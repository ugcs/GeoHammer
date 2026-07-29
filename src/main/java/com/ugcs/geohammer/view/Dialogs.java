package com.ugcs.geohammer.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.feedback.Attachment;
import com.ugcs.geohammer.feedback.FeedbackView;
import com.ugcs.geohammer.feedback.FileAttachment;
import com.ugcs.geohammer.format.FileOpenException;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class Dialogs {

	private static final String DEFAULT_ALERT_TITLE = "Alert";

	private static final double CONTENT_WIDTH = 400.0;

	private static final int CONTENT_MAX_ROWS = 8;

	public static final ButtonType REPORT_ISSUE = new ButtonType("Report issue", ButtonBar.ButtonData.OK_DONE);

	public static final ButtonType SUBMIT_FEEDBACK = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);

	private static Node createContent(String message) {
		TextArea textArea = new TextArea(message);
		// hide border and background
		textArea.getStyleClass().addAll("label");
		textArea.setEditable(false);
		textArea.setWrapText(true);
		textArea.setFocusTraversable(false);

		textArea.setPrefWidth(CONTENT_WIDTH);

		updateContentHeight(textArea, CONTENT_WIDTH);
		Listeners.onChange(textArea.widthProperty(), width
				-> updateContentHeight(textArea, width.doubleValue()));

		return textArea;
	}

	private static void updateContentHeight(TextArea textArea, double width) {
		ScrollPane viewport = (ScrollPane)textArea.lookup(".scroll-pane");
		Region content = viewport != null
				? (Region)viewport.lookup(".content")
				: null;
		double padding = getHorizontalPadding(textArea)
				+ getHorizontalPadding(viewport)
				+ getHorizontalPadding(content);
		double wrappingWidth = Math.max(1.0, width - padding);

		int numRows = Views.estimateTextRows(textArea.getText(), wrappingWidth, textArea.getFont());
		numRows = Math.clamp(numRows, 1, CONTENT_MAX_ROWS);

		double verticalPadding = getVerticalPadding(textArea)
				+ getVerticalPadding(viewport)
				+ getVerticalPadding(content);
		double height = numRows * Views.estimateRowHeight(textArea.getFont()) + verticalPadding;
		textArea.setMinHeight(height);
		textArea.setPrefHeight(height);
		textArea.setMaxHeight(height);

		if (viewport != null) {
			viewport.setHbarPolicy(ScrollBarPolicy.NEVER);
			viewport.setVbarPolicy(numRows < CONTENT_MAX_ROWS
					? ScrollBarPolicy.NEVER
					: ScrollBarPolicy.AS_NEEDED);
		}
	}

	private static double getHorizontalPadding(@Nullable Region region) {
		if (region == null) {
			return 0;
		}
		Insets insets = region.getInsets();
		return Math.round(insets.getLeft()) + Math.round(insets.getRight());
	}

	private static double getVerticalPadding(@Nullable Region region) {
		if (region == null) {
			return 0;
		}
		Insets insets = region.getInsets();
		return Math.round(insets.getTop()) + Math.round(insets.getBottom());
	}

	private static Alert createAlert(AlertType alertType, String header, String message) {
		if (alertType == null) {
			alertType = AlertType.NONE;
		}

		Alert alert = new Alert(alertType);
		alert.setTitle(getAlertTitle(alertType));
		alert.setHeaderText(header);

		alert.getDialogPane().setContent(createContent(message));
		alert.initOwner(AppContext.stage);

		AppContext.getInstance(ThemeService.class)
				.registerScene(alert.getDialogPane().getScene(), false);
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

	public static void showError(String header, Throwable t) {
		String message = t != null ? t.getMessage() : null;
		showError(header, message, t);
	}

	public static void showError(String header, String message, Throwable t) {
		// extra attachments
		List<Attachment> attachments = t instanceof FileOpenException fileOpenException
				? List.of(new FileAttachment(fileOpenException.getFile().toPath()))
				: List.of();
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
				feedback.submit(attachments);
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

			AppContext.getInstance(ThemeService.class)
					.registerScene(dialogPane.getScene(), false);
			dialog.show();
		});
	}

	public static void showWarning(String header, String message) {
		Platform.runLater(() -> {
			Alert alert = createAlert(AlertType.WARNING, header, message);
			alert.show();
		});
	}
}

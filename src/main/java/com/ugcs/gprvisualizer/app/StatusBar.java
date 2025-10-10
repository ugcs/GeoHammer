package com.ugcs.gprvisualizer.app;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.input.MouseButton;
import org.springframework.stereotype.Component;

import com.ugcs.gprvisualizer.app.intf.Status;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

@Component
public class StatusBar extends GridPane implements Status {

	private static final int MAX_MESSAGES = 50;

	private TextField textField = new TextField();

	private final Deque<StatusMessage> messageHistory = new ArrayDeque<>(MAX_MESSAGES);

	private Popup historyPopup;

	{
		textField.setEditable(false);
		textField.setStyle("-fx-background-color: transparent; "
				+ "-fx-border-color: transparent; "
				+ "-fx-focus-color: transparent; "
				+ "-fx-faint-focus-color: transparent;"
		);
	}

	public StatusBar(TaskStatusView taskStatusView) {
		ColumnConstraints column1 = new ColumnConstraints(16);
		this.getColumnConstraints().add(column1);

        ColumnConstraints column2 = new ColumnConstraints(150, 150, Double.MAX_VALUE);
        column2.setHgrow(Priority.ALWAYS);
        this.getColumnConstraints().add(column2);

        ColumnConstraints column3 = new ColumnConstraints(70, 70, Double.MAX_VALUE);
        column3.setHgrow(Priority.ALWAYS);
        this.getColumnConstraints().add(column3);

		ColumnConstraints column4 = new ColumnConstraints(300, 300, Double.MAX_VALUE);
		column3.setHgrow(Priority.ALWAYS);
		this.getColumnConstraints().add(column4);

		this.add(new Label(), 0, 0);
		this.add(textField, 1, 0);
		this.add(new Label(), 2, 0);
		this.add(taskStatusView, 3, 0);

		GridPane.setHalignment(taskStatusView, HPos.RIGHT);
		GridPane.setMargin(taskStatusView, new Insets(0, 20, 0, 20));

		// Add click handler to show message history
		textField.setOnMouseClicked(e -> {
			if (MouseButton.PRIMARY.equals(e.getButton()) && e.getClickCount() != 2) {
				showMessageHistory(e);
			}
		});

		AppContext.status = this;
	}

	/**
	 * Shows a message in the status bar.
	 * 
	 * @param txt The message text
	 */
	public void showProgressText(String txt) {
		showMessage(txt, "System");
	}

	/**
	 * Shows a message in the status bar with a specified source.
	 * 
	 * @param message The message text
	 * @param source The source of the message
	 */
	public void showMessage(String message, String source) {
		StatusMessage statusMessage = new StatusMessage(message, source);

		Platform.runLater(() -> {
			// Add to history
			addToHistory(statusMessage);

			// Update text field
			textField.setText(statusMessage.getShortFormattedMessage());
		});
	}

	/**
	 * Adds a message to the history, maintaining the maximum size.
	 * 
	 * @param message The message to add
	 */
	private void addToHistory(StatusMessage message) {
		// Add to front of queue
		messageHistory.addFirst(message);

		// Remove oldest if we exceed the maximum
		if (messageHistory.size() > MAX_MESSAGES) {
			messageHistory.removeLast();
		}
	}

	/**
	 * Shows the message history in a popup.
	 * 
	 * @param event The mouse event
	 */
	private void showMessageHistory(MouseEvent event) {
		if (messageHistory.isEmpty()) {
			return;
		}

		if (historyPopup != null && historyPopup.isShowing()) {
			historyPopup.hide();
			return;
		}

		// Create a ListView to display messages
		ListView<String> listView = new ListView<>();
		listView.getItems().addAll(
			messageHistory.stream()
				.map(StatusMessage::getFormattedMessage)
				.collect(Collectors.toList())
		);
		listView.setPrefHeight(Math.min(messageHistory.size() * 48, 400));
		listView.setPrefWidth(800);

		// Create a VBox to hold the ListView
		VBox vbox = new VBox(listView);
		//vbox.setPadding(new Insets(2));
		vbox.setStyle("-fx-background-color: white; -fx-border-color: gray; -fx-border-width: 0;");

		// Create and show the popup
		historyPopup = new Popup();
		historyPopup.getContent().add(vbox);
		historyPopup.setAutoHide(true);
		historyPopup.show(textField, event.getScreenX(), event.getScreenY());
	}
}

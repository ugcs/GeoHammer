package com.ugcs.geohammer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

import com.ugcs.geohammer.release.ReleaseView;
import com.ugcs.geohammer.view.Styles;
import com.ugcs.geohammer.view.Toast;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.view.status.StatusMessage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StatusBar extends HBox implements Status, InitializingBean {

	private static final int MAX_MESSAGES = 50;

	private final TextField textField = new TextField();

    private final Label versionStatus = new Label();

    @Autowired
    public BuildInfo buildInfo;

    @Autowired
    public ReleaseView releaseView;

	private final Deque<StatusMessage> messageHistory = new ArrayDeque<>(MAX_MESSAGES);

	private Popup historyPopup;

	public StatusBar(TaskStatusView taskStatusView) {
        textField.setEditable(false);
        textField.setMinWidth(150);
        textField.setPrefWidth(400);
        textField.setStyle(
                "-fx-background-color: transparent; " +
                "-fx-border-color: transparent; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent;");

        // Add click handler to show message history
        textField.setOnMouseClicked(e -> {
            if (MouseButton.PRIMARY.equals(e.getButton()) && e.getClickCount() != 2) {
                showMessageHistory(e);
            }
        });
        HBox.setHgrow(textField, Priority.ALWAYS);

        Styles.addResource(versionStatus, Styles.STATUS_STYLE_PATH);
        versionStatus.setId("version-status");
        versionStatus.setOnMouseClicked(e -> {
            releaseView.showAbove(versionStatus);
            releaseView.update();
        });

        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(0, 16, 0, 16));
        getChildren().addAll(textField, Views.createSpacer(), taskStatusView, versionStatus);

		AppContext.status = this;
	}

    @Override
    public void afterPropertiesSet() {
        versionStatus.setText(buildInfo.getBuildVersion());
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

		listView.setOnMouseClicked(me -> {
			if (!listView.getSelectionModel().isEmpty() && MouseButton.PRIMARY.equals(me.getButton())) {
				String selected = listView.getSelectionModel().getSelectedItem();
				if (selected != null && !selected.isEmpty()) {
					copyToClipboard(selected);
					Toast.show("Copied to clipboard", textField, me.getScreenX(), me.getScreenY());
				}
			}
		});

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

	private void copyToClipboard(String text) {
		ClipboardContent content = new ClipboardContent();
		content.putString(text);
		Clipboard.getSystemClipboard().setContent(content);
	}
}

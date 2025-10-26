package com.ugcs.gprvisualizer.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.util.Duration;

public class Toast {

	private static final int VERTICAL_OFFSET = 30;
	private static final int DEFAULT_DISPLAY_TIME_MILLIS = 1000;

	public static void show(String message, Node owner, double screenX, double screenY) {
		show(message, owner, screenX, screenY, DEFAULT_DISPLAY_TIME_MILLIS);
	}

	public static void show(String message, Node owner, double screenX, double screenY, int millis) {
		Label label = new Label(message);
		label.setStyle("-fx-background-color: rgba(0,0,0,0.75); -fx-text-fill: white; -fx-padding: 8px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
		Popup toast = new Popup();
		toast.getContent().add(label);
		toast.setAutoFix(true);
		toast.setAutoHide(true);
		toast.show(owner, screenX, screenY - VERTICAL_OFFSET);

		Timeline timeline = new Timeline(new KeyFrame(Duration.millis(millis), ae -> toast.hide()));
		timeline.play();
	}
}

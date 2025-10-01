package com.ugcs.gprvisualizer.utils;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;

public final class Views {

    private Views() {
    }

    public static String toColorString(Color color) {
        Check.notNull(color);

        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    public static Button createGlyphButton(String text, int width, int height) {
        Label iconLabel = new Label(text);
        iconLabel.setStyle("-fx-font-family: 'System', 'Arial', sans-serif;"
                + "-fx-text-fill: white;"
                + "-fx-alignment: center;"
                + "-fx-text-alignment: center;"
        );

        Button button = new Button();
        button.setGraphic(iconLabel);
        button.setStyle("-fx-background-color: #a0a0a0;" +
                "-fx-background-radius: 50%;" +
                "-fx-alignment: center;" +
                "-fx-content-display: graphic-only;"
        );
        button.setPadding(new Insets(2));
        button.setPrefSize(width, height);
        button.setMinSize(width, height);
        button.setMaxSize(width, height);
        button.setCursor(Cursor.HAND);
        return button;
    }
}

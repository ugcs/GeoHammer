package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public final class Views {

    private Views() {
    }

    public static Color fxColor(java.awt.Color color) {
        return Color.rgb(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                color.getAlpha() / 255.0);
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
        iconLabel.setStyle(
                "-fx-font-family: 'System', 'Arial', sans-serif;" +
                "-fx-text-fill: white;" +
                "-fx-alignment: center;" +
                "-fx-text-alignment: center;"
        );

        Button button = new Button();
        button.setGraphic(iconLabel);
        button.setStyle(
                "-fx-background-color: #a0a0a0;" +
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

    public static Button createFlatButton(String text, int height) {
        Button button = new Button(text);
        button.setTextFill(Color.WHITE);
        button.setFont(Font.font("Arial", 12));
        button.setPadding(new Insets(0, 8, 0, 8));
        button.setAlignment(Pos.CENTER);
        button.setCursor(Cursor.HAND);

        button.setStyle(
                "-fx-background-color: #a0a0a0;" +
                "-fx-background-radius: " + height / 2 + ";"
        );

        button.setPrefHeight(height);
        button.setMinHeight(height);
        button.setMaxHeight(height);

        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);

        return button;
    }

    public static Button createSvgButton(String svg, Color color, double height, String tooltip) {
        Button button = new Button();
        button.setStyle("-fx-background-color: transparent; -fx-padding: 2px; -fx-cursor: hand;");
        button.setTooltip(new Tooltip(tooltip));
        ResourceImageHolder.setButtonImage(svg, color, height, button);
        return button;
    }

	public static void tintImage(ImageView imageView, @SuppressWarnings("SameParameterValue") Color tint) {
		Image image = imageView.getImage();
		if (image == null) {
			return;
		}
		ColorInput colorInput = new ColorInput(0, 0, image.getWidth(), image.getHeight(), tint);
		Blend blend = new Blend(BlendMode.SRC_ATOP, null, colorInput);
		imageView.setEffect(blend);
	}

	public static Label createLabel(String text, int width) {
		Label label = new Label(text == null ? "-" : text);
		label.setPrefWidth(width);
		return label;
	}

    public static TextField createSelectableLabel(String text) {
        TextField textField = new TextField(text);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setBackground(Background.EMPTY);
        textField.setBorder(Border.EMPTY);
        textField.setPadding(new Insets(0));
        return textField;
    }

	public static Label createFixedLabel(String text, int width) {
		Label label = new Label(text);
		label.setPrefWidth(width);
		label.setMinWidth(width);
		return label;
	}

	public static Region createSpacer() {
		Region region = new Region();
		HBox.setHgrow(region, Priority.ALWAYS);
		return region;
	}

	public static Region createVerticalSeparator() {
		Region separator = new Region();
		separator.setPrefWidth(1);
		separator.setStyle("-fx-background-color: #cccccc;");
		return separator;
	}
}

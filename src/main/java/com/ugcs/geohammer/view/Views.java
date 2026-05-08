package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Nulls;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class Views {

    private Views() {
    }

    public static void runNowOrLater(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    public static Screen getScreen(Window window) {
        if (window == null) {
            return Screen.getPrimary();
        }
        List<Screen> screens = Screen.getScreensForRectangle(
                window.getX(),
                window.getY(),
                window.getWidth(),
                window.getHeight());
        return !Nulls.isNullOrEmpty(screens)
                ? screens.getFirst()
                : Screen.getPrimary();
    }

    public static Button createGlyphButton(String text, int width, int height) {
        Label icon = new Label(text);
        icon.getStyleClass().add("glyph-icon");

        Button button = new Button();
        button.getStyleClass().add("glyph");
        button.setGraphic(icon);

        button.setPrefSize(width, height);
        button.setMinSize(width, height);
        button.setMaxSize(width, height);

        return button;
    }

    public static Button createSvgButton(String svg, double height, String tooltip) {
        Button button = new Button();
        button.getStyleClass().add("icon");
        button.setTooltip(new Tooltip(tooltip));
        ResourceImageHolder.setButtonImage(svg, height, button);
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

	public static java.awt.Image tintImage(java.awt.Image image, java.awt.Color color) {
		BufferedImage tintedImage = new BufferedImage(
				image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = tintedImage.createGraphics();
		g2d.drawImage(image, 0, 0, null);
		g2d.setComposite(AlphaComposite.SrcAtop);
		g2d.setColor(color);
		g2d.fillRect(0, 0, image.getWidth(null), image.getHeight(null));
		g2d.dispose();
		return tintedImage;
	}

	public static Label createLabel(String text, int width) {
		Label label = new Label(text == null ? "-" : text);
		label.setPrefWidth(width);
		return label;
	}

    public static TextField createSelectableLabel(String text) {
        TextField textField = new TextField(text);
        textField.getStyleClass().addAll("label", "no-padding");
        textField.setEditable(false);
        textField.setFocusTraversable(false);
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
        VBox.setVgrow(region, Priority.ALWAYS);
		return region;
	}

	public static Region createVerticalSeparator() {
		Region separator = new Region();
        separator.getStyleClass().add("vertical-separator");
		return separator;
	}

    public static Region createHorizontalSeparator() {
        Region separator = new Region();
        separator.getStyleClass().add("horizontal-separator");
        return separator;
    }

    public static ScrollPane createVerticalScrollContainer(Node content, Node parent) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // set reasonably large amount to fit tab height;
        // this seems the only way to force pane to fill container
        // in height
        scrollPane.setPrefHeight(10_000);
        if (parent != null) {
            scrollPane.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    // redirect focus to the parent node
                    Platform.runLater(parent::requestFocus);
                }
            });
        }
        return scrollPane;
    }
}

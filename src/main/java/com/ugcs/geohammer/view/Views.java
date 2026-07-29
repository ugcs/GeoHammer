package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import javafx.application.Platform;
import javafx.geometry.Insets;
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
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class Views {

    public static final double DEFAULT_SPACING = 5;

    public static final Insets DEFAULT_OPTIONS_INSETS = new Insets(10, 0, 5, 0);

    public static final double LABEL_SPACING = 4;

    public static final double TOP_LABEL_SPACING = 3;

    public static final Insets TOP_LABEL_INSETS = new Insets(0, 0, 0, 2);

    public static final List<String> TOP_LABEL_STYLES = List.of("dim");

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

    public static double estimateRowHeight(Font font) {
        Text temp = new Text("W");
        temp.setFont(font);
        temp.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
        return temp.getLayoutBounds().getHeight();
    }

    public static int estimateTextRows(String text, double wrappingWidth, Font font) {
        if (Strings.isNullOrEmpty(text)) {
            return 0;
        }

        Text temp = new Text("W");
        temp.setFont(font);
        // text layouts are cached by a content;
        // measure in the same type to keep a row height and a text height in sync
        temp.setBoundsType(TextBoundsType.LOGICAL_VERTICAL_CENTER);
        double rowHeight = temp.getLayoutBounds().getHeight();
        if (rowHeight <= 0) {
            return 0;
        }
        temp.setWrappingWidth(wrappingWidth);
        temp.setText(text);
        double textHeight = temp.getLayoutBounds().getHeight();
        return (int)Math.ceil(textHeight / rowHeight);
    }
}

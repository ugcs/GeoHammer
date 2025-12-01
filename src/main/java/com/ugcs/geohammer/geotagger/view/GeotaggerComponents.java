package com.ugcs.geohammer.geotagger.view;

import com.ugcs.geohammer.view.ResourceImageHolder;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
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

public class GeotaggerComponents {
	private static final String SEPARATOR_STYLE = "-fx-background-color: #cccccc;";

	public static Node createDragAndDropPlaceholder(String text) {
		VBox box = new VBox(5);
		box.setAlignment(Pos.CENTER);
		ImageView uploadImage = ResourceImageHolder.getImageView("upload_file.png");
		if (uploadImage == null) {
			return null;
		}
		uploadImage.setFitHeight(32);
		uploadImage.setFitWidth(32);
		tintImageView(uploadImage, Color.GRAY);
		Label label = new Label(text);
		label.setTextFill(Color.GRAY);
		box.getChildren().addAll(uploadImage, label);
		return box;
	}

	protected static void tintImageView(ImageView imageView, @SuppressWarnings("SameParameterValue") Color tint) {
		Image img = imageView.getImage();
		if (img == null) return;
		Blend blend = new Blend(BlendMode.SRC_ATOP, null, new ColorInput(0, 0, img.getWidth(), img.getHeight(), tint));
		imageView.setEffect(blend);
	}

	public static Label headerLabel(String text, int width) {
		Label label = new Label(text);
		label.setPrefWidth(width);
		label.setMinWidth(width);
		return label;
	}

	public static Label fixedLabel(String text, int width) {
		Label label = new Label(text == null ? "-" : text);
		label.setPrefWidth(width);
		return label;
	}

	public static Region spacer() {
		Region region = new Region();
		HBox.setHgrow(region, Priority.ALWAYS);
		return region;
	}

	public static Region createVerticalSeparator() {
		Region separator = new Region();
		separator.setPrefWidth(1);
		separator.setStyle(SEPARATOR_STYLE);
		return separator;
	}
}

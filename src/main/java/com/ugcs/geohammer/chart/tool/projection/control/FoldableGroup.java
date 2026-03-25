package com.ugcs.geohammer.chart.tool.projection.control;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class FoldableGroup extends VBox {

    private static final String COLLAPSED = "⏵";

    private static final String EXPANDED = "⏷";

    private final Label icon;

    private final VBox content;

    public FoldableGroup(String text, Node... children) {
        Label title = new Label(text);
        title.getStyleClass().add("group-title");

        icon = new Label(EXPANDED);
        icon.getStyleClass().add("group-icon");

        HBox header = new HBox(icon, title);
        header.getStyleClass().add("group-header");
        header.setOnMouseClicked(e -> toggle());

        content = new VBox(children);
        content.getStyleClass().add("group-content");

        getStyleClass().add("group");
        VBox.setMargin(this, new Insets(4, 0, 4, 0));
        getChildren().addAll(header, content);
    }

    public void toggle() {
        boolean expand = !content.isVisible();

        content.setVisible(expand);
        content.setManaged(expand);
        icon.setText(expand ? EXPANDED : COLLAPSED);
    }
}

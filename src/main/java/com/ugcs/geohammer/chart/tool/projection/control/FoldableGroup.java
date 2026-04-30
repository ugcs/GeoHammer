package com.ugcs.geohammer.chart.tool.projection.control;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class FoldableGroup extends VBox {

    private static final String FOLDED = "⏵";

    private static final String EXPANDED = "⏷";

    private final Label icon;

    private final VBox content;

    public FoldableGroup(String text, Node... children) {
        setSpacing(8);

        Label title = new Label(text);
        title.getStyleClass().add("group-title");

        icon = new Label(EXPANDED);
        icon.getStyleClass().add("group-icon");

        HBox header = new HBox(4, icon, title);
        header.getStyleClass().add("group-header");
        header.setOnMouseClicked(e -> toggle());

        content = new VBox(6, children);
        content.getStyleClass().add("group-content");

        getStyleClass().add("group");
        getChildren().addAll(header, content);
    }

    public boolean isFolded() {
        return !content.isVisible();
    }

    public void setFolded(boolean value) {
        content.setVisible(!value);
        content.setManaged(!value);
        icon.setText(value ? FOLDED : EXPANDED);
    }

    public void toggle() {
        setFolded(!isFolded());
    }
}

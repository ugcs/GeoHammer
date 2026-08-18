package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Views;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class NodeWithTopLabel<T extends Node> extends VBox {

    protected final Label label;

    protected final T node;

    public NodeWithTopLabel(String text, T node) {
        label = new Label(text);
        label.getStyleClass().addAll(Views.TOP_LABEL_STYLES);
        label.setPadding(Views.TOP_LABEL_INSETS);

        this.node = Check.notNull(node);

        setSpacing(Views.TOP_LABEL_SPACING);
        getChildren().addAll(label, this.node);
    }

    public Label getLabel() {
        return label;
    }

    public T getNode() {
        return node;
    }
}

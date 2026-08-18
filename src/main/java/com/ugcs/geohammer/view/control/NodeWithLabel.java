package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Views;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class NodeWithLabel<T extends Node> extends HBox {

    protected final Label label;

    protected final T node;

    public NodeWithLabel(String text, T node) {
        this(text, node, true);
    }

    public NodeWithLabel(String text, T node, boolean withSpacer) {
        label = new Label(text);

        this.node = Check.notNull(node);

        setSpacing(Views.DEFAULT_SPACING);
        setAlignment(Pos.BASELINE_LEFT);

        ObservableList<Node> children = getChildren();
        children.add(label);
        if (withSpacer) {
            children.add(Views.createSpacer());
        }
        children.add(this.node);
    }

    public Label getLabel() {
        return label;
    }

    public T getNode() {
        return node;
    }
}

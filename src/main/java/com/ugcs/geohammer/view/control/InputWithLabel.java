package com.ugcs.geohammer.view.control;

import javafx.geometry.Pos;
import javafx.scene.control.TextField;

public class InputWithLabel extends NodeWithLabel<TextField> {

    public InputWithLabel(String text) {
        this(text, true);
    }

    public InputWithLabel(String text, boolean withSpacer) {
        super(text, new TextField(), withSpacer);

        node.setPrefWidth(80);
        node.setAlignment(Pos.BASELINE_RIGHT);
    }

    public TextField getInput() {
        return node;
    }
}

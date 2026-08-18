package com.ugcs.geohammer.view.control;

import javafx.scene.control.TextField;

public class InputWithTopLabel extends NodeWithTopLabel<TextField> {

    public InputWithTopLabel(String text) {
        super(text, new TextField());

        node.setMaxWidth(Double.MAX_VALUE);
    }

    public TextField getInput() {
        return node;
    }
}

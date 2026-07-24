package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.view.Views;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class InputWithLabel extends HBox {

    protected final TextField input;

    public InputWithLabel(String text) {
        Label label = new Label(text);

        input = new TextField();
        input.setPrefWidth(80);
        input.setAlignment(Pos.BASELINE_RIGHT);

        setSpacing(Views.LABEL_SPACING);
        setAlignment(Pos.BASELINE_LEFT);
        getChildren().addAll(label, Views.createSpacer(), input);
    }

    public TextField getInput() {
        return input;
    }
}

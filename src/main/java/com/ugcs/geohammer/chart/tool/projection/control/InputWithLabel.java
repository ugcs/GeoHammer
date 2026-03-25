package com.ugcs.geohammer.chart.tool.projection.control;

import com.ugcs.geohammer.view.Views;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class InputWithLabel extends HBox {

    private static final double SPACING = 4;

    private final TextField input;

    public InputWithLabel(String text) {
        Label label = new Label(text);

        input = new TextField();
        input.setPrefWidth(80);
        input.setAlignment(Pos.BASELINE_RIGHT);

        setSpacing(SPACING);
        setAlignment(Pos.BASELINE_LEFT);
        getChildren().addAll(label, Views.createSpacer(), input);
    }

    public TextField getInput() {
        return input;
    }
}

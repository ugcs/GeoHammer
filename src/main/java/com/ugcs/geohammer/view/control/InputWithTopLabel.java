package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.view.Views;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class InputWithTopLabel extends VBox {

    protected final TextField input;

    public InputWithTopLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll(Views.TOP_LABEL_STYLES);
        label.setPadding(Views.TOP_LABEL_INSETS);

        input = new TextField();
        input.setMaxWidth(Double.MAX_VALUE);

        setSpacing(Views.TOP_LABEL_SPACING);
        getChildren().addAll(label, input);
    }

    public TextField getInput() {
        return input;
    }

    public String getText() {
        return input.getText();
    }

    public void setText(String text) {
        input.setText(text);
    }
}

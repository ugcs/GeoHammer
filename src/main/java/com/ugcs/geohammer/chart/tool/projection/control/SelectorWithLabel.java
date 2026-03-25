package com.ugcs.geohammer.chart.tool.projection.control;

import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.view.Views;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;

public class SelectorWithLabel<T> extends HBox {

    private static final double SPACING = 4;

    private final ComboBox<T> selector;

    public SelectorWithLabel(String text, List<T> values) {
        Label label = new Label(text);

        selector = new ComboBox<>();
        selector.setMinWidth(70);
        selector.setPrefWidth(140);
        ObservableList<T> items = FXCollections.observableList(Nulls.toEmpty(values));
        selector.setItems(items);

        setSpacing(SPACING);
        setAlignment(Pos.BASELINE_LEFT);
        getChildren().addAll(label, Views.createSpacer(), selector);
    }

    public ComboBox<T> getSelector() {
        return selector;
    }
}

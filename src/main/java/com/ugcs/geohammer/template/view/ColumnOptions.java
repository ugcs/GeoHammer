package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.template.DateTimeFormats;
import com.ugcs.geohammer.template.model.ColumnModel;
import com.ugcs.geohammer.template.model.ColumnType;
import com.ugcs.geohammer.template.model.Defaults;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import org.jspecify.annotations.Nullable;

public class ColumnOptions extends Button {

    private static final PseudoClass SET = PseudoClass.getPseudoClass("set");

    private static final double INPUT_WIDTH = 180;

    private final ColumnModel column;

    private final Popup popup = new Popup();

    public ColumnOptions(ColumnModel column) {
        this.column = column;

        setText("ƒ");
        setMinWidth(30);
        setPrefWidth(30);
        setTooltip(new Tooltip("Column options"));
        getStyleClass().add("column-options");

        popup.setAutoHide(true);
        setOnAction(e -> showOptions());

        Listeners.onChange(column.typeProperty(), v -> {
            popup.hide();
            updateHighlight();
        });
        Listeners.onChange(column.unitsProperty(), v -> updateHighlight());
        Listeners.onChange(column.decimalsProperty(), v -> updateHighlight());
        column.getFormats().addListener((ListChangeListener<String>)change -> updateHighlight());
        updateHighlight();
    }

    public static boolean supportsOptions(@Nullable ColumnType type) {
        if (type == null) {
            return false;
        }
        return switch (type) {
            case DATE_TIME, DATE, TIME, VALUE -> true;
            default -> false;
        };
    }

    private void showOptions() {
        popup.getContent().setAll(createContent());
        // owned by the window: the button node can be recreated by
        // a table refresh while the popup is open
        Bounds bounds = localToScreen(getBoundsInLocal());
        popup.show(getScene().getWindow(), bounds.getMinX(), bounds.getMaxY() + 2);
    }

    private Node createContent() {
        HBox content = new HBox(6);
        content.getStyleClass().add("popover");
        content.setAlignment(Pos.CENTER_LEFT);

        ColumnType type = column.getType();
        if (!DateTimeFormats.knownFormats(type).isEmpty()) {
            ListField formats = new ListField(DateTimeFormats.knownFormats(type));
            formats.setPrefWidth(INPUT_WIDTH);
            formats.setOnAction(e -> popup.hide());
            Bindings.bindContentBidirectional(formats.getItems(), column.getFormats());
            content.getChildren().addAll(new Label("Formats"), formats);
        }

        if (type == ColumnType.VALUE) {
            ComboBox<String> units = new ComboBox<>(FXCollections.observableArrayList(Defaults.UNITS));
            units.setEditable(true);
            units.setPrefWidth(90);
            units.valueProperty().bindBidirectional(column.unitsProperty());
            units.getEditor().setOnAction(e -> popup.hide());
            content.getChildren().addAll(new Label("Units"), units);

            TextField decimals = new TextField(Integer.toString(column.getDecimals()));
            decimals.setPrefWidth(50);
            Listeners.onChange(decimals.textProperty(), text -> {
                try {
                    column.setDecimals(Integer.parseInt(text.trim()));
                } catch (NumberFormatException e) {
                    // keep the last valid value
                }
            });
            decimals.setOnAction(e -> popup.hide());
            content.getChildren().addAll(new Label("Decimals"), decimals);
        }

        return content;
    }

    private void updateHighlight() {
        pseudoClassStateChanged(SET, hasOptions());
    }

    private boolean hasOptions() {
        return switch (column.getType()) {
            case DATE_TIME, DATE, TIME -> !column.getFormats().isEmpty();
            case VALUE -> !Strings.isNullOrBlank(column.getUnits())
                    || column.getDecimals() != BaseData.DEFAULT_DECIMALS;
            default -> false;
        };
    }
}

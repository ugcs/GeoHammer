package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.util.ReentranceGuard;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.ArrayList;
import java.util.List;

public class ListField extends HBox {

    private final ObservableList<String> items = FXCollections.observableArrayList();

    private final TextField input = new TextField();

    private final ReentranceGuard itemsGuard = new ReentranceGuard();

    public ListField(List<String> suggestions) {
        input.setPromptText("Comma-separated");
        Listeners.onChange(input.textProperty(), this::onTextChanged);
        HBox.setHgrow(input, Priority.ALWAYS);

        items.addListener((ListChangeListener<String>)change -> onItemsChanged());

        MenuButton selector = createSuggestionSelector(suggestions);

        setSpacing(Views.DEFAULT_SPACING);
        setAlignment(Pos.BASELINE_LEFT);
        getChildren().addAll(input, selector);
        onItemsChanged();
    }

    public ObservableList<String> getItems() {
        return items;
    }

    public void setOnAction(EventHandler<ActionEvent> handler) {
        input.setOnAction(handler);
    }

    private MenuButton createSuggestionSelector(List<String> suggestions) {
        MenuButton selector = new MenuButton("+");
        for (String suggestion : Nulls.toEmpty(suggestions)) {
            MenuItem menuItem = new MenuItem(suggestion);
            menuItem.setOnAction(e -> {
                if (!items.contains(suggestion)) {
                    items.add(suggestion);
                }
            });
            selector.getItems().add(menuItem);
        }
        selector.setDisable(selector.getItems().isEmpty());
        return selector;
    }

    private void onTextChanged(String text) {
        itemsGuard.run(() -> items.setAll(parseItems(text)));
    }

    private void onItemsChanged() {
        itemsGuard.run(() -> input.setText(String.join(", ", items)));
    }

    private static List<String> parseItems(String text) {
        List<String> parsed = new ArrayList<>();
        for (String token : Strings.nullToEmpty(text).split(",")) {
            token = token.trim();
            if (!token.isEmpty() && !parsed.contains(token)) {
                parsed.add(token);
            }
        }
        return parsed;
    }
}

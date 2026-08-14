package com.ugcs.geohammer.template.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class FormatModel {

    private final StringProperty commentPrefix = new SimpleStringProperty(Defaults.COMMENT_PREFIX);

    private final ObservableList<String> separators = FXCollections.observableArrayList(Defaults.SEPARATOR);

    private final BooleanProperty repeatableSeparator = new SimpleBooleanProperty(false);

    private final BooleanProperty hasHeader = new SimpleBooleanProperty(true);

    public String getCommentPrefix() {
        return commentPrefix.get();
    }

    public StringProperty commentPrefixProperty() {
        return commentPrefix;
    }

    public ObservableList<String> getSeparators() {
        return separators;
    }

    public boolean isRepeatableSeparator() {
        return repeatableSeparator.get();
    }

    public BooleanProperty repeatableSeparatorProperty() {
        return repeatableSeparator;
    }

    public boolean isHasHeader() {
        return hasHeader.get();
    }

    public BooleanProperty hasHeaderProperty() {
        return hasHeader;
    }

    public void reset() {
        commentPrefix.set(Defaults.COMMENT_PREFIX);
        separators.setAll(Defaults.SEPARATOR);
        repeatableSeparator.set(false);
        hasHeader.set(true);
    }
}

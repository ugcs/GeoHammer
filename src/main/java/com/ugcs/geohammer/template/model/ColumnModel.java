package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.util.Strings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jspecify.annotations.Nullable;

public class ColumnModel {

    private final StringProperty header = new SimpleStringProperty();

    private final ObjectProperty<ColumnType> type = new SimpleObjectProperty<>(ColumnType.NONE);

    private final ObjectProperty<DateSource> dateSource = new SimpleObjectProperty<>(DateSource.COLUMN);

    private final StringProperty regex = new SimpleStringProperty(Strings.empty());

    private final ObservableList<String> formats = FXCollections.observableArrayList();

    private final StringProperty units = new SimpleStringProperty(Strings.empty());

    private final IntegerProperty decimals = new SimpleIntegerProperty(BaseData.DEFAULT_DECIMALS);

    public ColumnModel(@Nullable String header) {
        setHeader(header);
    }

    public @Nullable String getHeader() {
        return header.get();
    }

    public void setHeader(@Nullable String value) {
        header.set(value);
    }

    public StringProperty headerProperty() {
        return header;
    }

    public ColumnType getType() {
        return type.get();
    }

    public void setType(ColumnType value) {
        type.set(value);
    }

    public ObjectProperty<ColumnType> typeProperty() {
        return type;
    }

    public DateSource getDateSource() {
        return dateSource.get();
    }

    public void setDateSource(DateSource value) {
        dateSource.set(value);
    }

    public ObjectProperty<DateSource> dateSourceProperty() {
        return dateSource;
    }

    public String getRegex() {
        return regex.get();
    }

    public void setRegex(String value) {
        regex.set(value);
    }

    public StringProperty regexProperty() {
        return regex;
    }

    public ObservableList<String> getFormats() {
        return formats;
    }

    public String getUnits() {
        return units.get();
    }

    public void setUnits(String value) {
        units.set(value);
    }

    public StringProperty unitsProperty() {
        return units;
    }

    public int getDecimals() {
        return decimals.get();
    }

    public void setDecimals(int value) {
        decimals.set(value);
    }

    public IntegerProperty decimalsProperty() {
        return decimals;
    }

    public enum DateSource {
        COLUMN,
        FILE_NAME
    }
}

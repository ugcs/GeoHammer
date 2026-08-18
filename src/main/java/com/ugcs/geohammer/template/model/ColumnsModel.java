package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.util.Strings;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jspecify.annotations.Nullable;

public class ColumnsModel {

    // scanned columns in the file order; effectively an ordered
    // header -> column map; extractor makes the list fire updates
    // on column attribute changes
    private final ObservableList<ColumnModel> columns = FXCollections.observableArrayList(
            column -> new Observable[] {
                    column.typeProperty(),
                    column.regexProperty(),
                    column.getFormats(),
                    column.unitsProperty(),
                    column.decimalsProperty()
            });

    public ObservableList<ColumnModel> getColumns() {
        return columns;
    }

    public @Nullable ColumnModel getColumn(@Nullable String header) {
        if (Strings.isNullOrEmpty(header)) {
            return null;
        }
        for (ColumnModel column : columns) {
            if (header.equals(column.getHeader())) {
                return column;
            }
        }
        return null;
    }

    public @Nullable ColumnModel getColumn(ColumnType type) {
        for (ColumnModel column : columns) {
            if (column.getType() == type) {
                return column;
            }
        }
        return null;
    }

    public ColumnType getColumnType(@Nullable String header) {
        ColumnModel column = getColumn(header);
        return column != null ? column.getType() : ColumnType.NONE;
    }

    public void setColumnType(@Nullable String header, ColumnType type) {
        ColumnModel column = getColumn(header);
        if (column == null || column.getType() == type) {
            return;
        }
        // unique types: reset the previous assignment
        if (type != ColumnType.NONE && type != ColumnType.VALUE) {
            for (ColumnModel other : columns) {
                if (other != column && isConflicting(other.getType(), type)) {
                    other.setType(ColumnType.NONE);
                }
            }
        }
        column.setType(type);
    }

    private static boolean isConflicting(ColumnType mapped, ColumnType selected) {
        if (mapped == selected) {
            return true;
        }
        return switch (selected) {
            case DATE_TIME, TIMESTAMP -> mapped == ColumnType.DATE_TIME
                    || mapped == ColumnType.DATE
                    || mapped == ColumnType.TIME
                    || mapped == ColumnType.TIMESTAMP;
            case DATE, TIME -> mapped == ColumnType.DATE_TIME
                    || mapped == ColumnType.TIMESTAMP;
            default -> false;
        };
    }

    public boolean isTimeMapped() {
        for (ColumnModel column : columns) {
            switch (column.getType()) {
                case DATE_TIME, DATE, TIME, TIMESTAMP -> {
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    public void reset() {
        columns.clear();
    }
}

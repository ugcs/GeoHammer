package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.format.TraceFile;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class TraceSelection {

    private final ObjectProperty<TraceFile> file = new SimpleObjectProperty<>(null);

    private final ObservableList<Integer> fileLines = FXCollections.observableList(new ArrayList<>());

    private final ObjectProperty<Integer> line = new SimpleIntegerProperty(0).asObject();

    public TraceFile getFile() {
        return file.get();
    }

    public ObjectProperty<TraceFile> fileProperty() {
        return file;
    }

    public ObservableList<Integer> getFileLines() {
        return fileLines;
    }

    public Integer getLine() {
        return line.get();
    }

    public ObjectProperty<Integer> lineProperty() {
        return line;
    }
}

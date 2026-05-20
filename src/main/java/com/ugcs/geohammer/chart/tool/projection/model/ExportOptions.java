package com.ugcs.geohammer.chart.tool.projection.model;

import com.ugcs.geohammer.util.Strings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.stereotype.Component;

@Component
public class ExportOptions {

    private final StringProperty path = new SimpleStringProperty(Strings.empty());

    private final ObjectProperty<ExportScope> scope = new SimpleObjectProperty<>(ExportScope.SELECTED_LINE);

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private final BooleanProperty exporting = new SimpleBooleanProperty(false);

    public String getPath() {
        return path.get();
    }

    public StringProperty pathProperty() {
        return path;
    }

    public ExportScope getScope() {
        return scope.get();
    }

    public ObjectProperty<ExportScope> scopeProperty() {
        return scope;
    }

    public double getProgress() {
        return progress.get();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public boolean isExporting() {
        return exporting.get();
    }

    public BooleanProperty exportingProperty() {
        return exporting;
    }
}

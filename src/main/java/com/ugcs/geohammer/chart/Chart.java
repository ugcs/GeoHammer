package com.ugcs.geohammer.chart;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.TraceUnit;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public abstract class Chart extends ScrollableData implements FileDataContainer {

    public static final int MIN_HEIGHT = 400;

    protected final Model model;

    public Chart(Model model) {
        super(model);

        this.model = model;
    }

    protected boolean confirmUnsavedChanges() {
        SgyFile file = getFile();
        if (!file.isUnsaved()) {
            return true;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Warning");
        alert.setHeaderText("Current file is not saved. Continue?");
        alert.getButtonTypes().setAll(
                ButtonType.CANCEL,
                ButtonType.OK);
        alert.initOwner(AppContext.stage);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get().equals(ButtonType.OK);
    }

    public void close(boolean removeFromModel) {
        Node root = getRootNode();
        if (root.getParent() instanceof VBox parent) {
            // hide profile scroll
            getProfileScroll().setVisible(false);
            // remove charts
            parent.getChildren().remove(root);
            if (removeFromModel) {
                // remove files and traces from map
                model.publishEvent(new FileClosedEvent(this, getFile()));
            }
        }
    }

    public abstract SgyFile getFile();

    public abstract void init();

    // redraw
    public abstract void repaint();

    // reload data values to this chart
    public abstract void reload();

    // trace == null -> clear current selection
    public abstract void selectTrace(@Nullable TraceKey trace, boolean focus);

    public abstract int getSelectedLineIndex();

    public abstract void setTraceUnit(TraceUnit traceUnit);

    // flags

    public abstract List<FoundPlace> getFlags();

    public abstract void selectFlag(@Nullable FoundPlace flag);

    public abstract void addFlag(FoundPlace flag);

    public abstract void removeFlag(FoundPlace flag);

    public abstract void clearFlags();

    // zoom

    public abstract void zoomToCurrentLine();

    public abstract void zoomToPreviousLine();

    public abstract void zoomToNextLine();

    public abstract void zoomToFit();

    public abstract void zoomIn();

    public abstract void zoomOut();
}
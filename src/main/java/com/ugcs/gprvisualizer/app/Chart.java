package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public abstract class Chart extends ScrollableData implements FileDataContainer {

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

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get().equals(ButtonType.OK);
    }

    public abstract SgyFile getFile();

    // reload data values to this chart
    public abstract void reload();

    // trace == null -> clear current selection
    public abstract void selectTrace(@Nullable TraceKey trace, boolean focus);

    public abstract int getSelectedLineIndex();

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
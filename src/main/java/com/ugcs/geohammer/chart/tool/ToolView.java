package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.format.SgyFile;
import javafx.scene.layout.StackPane;

import java.util.Objects;

public abstract class ToolView extends StackPane {

    protected SgyFile selectedFile;

    public ToolView() {
        // hide by default
        show(false);
    }

    public void selectFile(SgyFile file) {
        selectFile(file, true);
    }

    public void selectFile(SgyFile file, boolean skipSelfUpdates) {
        if (skipSelfUpdates && Objects.equals(selectedFile, file)) {
            return;
        }
        selectedFile = file;

        loadPreferences();
        updateView();
    }

    public void updateView() {
    }

    public boolean isVisibleFor(SgyFile file) {
        return file != null;
    }

    public void show(boolean show) {
        setVisible(show);
        setManaged(show);
    }

    public void loadPreferences() {
    }

    public void savePreferences() {
    }
}

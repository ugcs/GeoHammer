package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import javafx.scene.layout.StackPane;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public abstract class ToolView extends StackPane {

    protected SgyFile selectedFile;

    public abstract void loadPreferences();

    public abstract void savePreferences();

    @EventListener
    protected void onFileSelected(FileSelectedEvent event) {
        if (Objects.equals(selectedFile, event.getFile())) {
            return;
        }
        selectedFile = event.getFile();
        loadPreferences();
    }
}

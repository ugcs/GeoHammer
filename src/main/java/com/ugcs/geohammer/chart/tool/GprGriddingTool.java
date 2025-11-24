package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.map.layer.radar.RadarMap;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GprGriddingTool extends ToolView {

    private final RadarMap radarMap;

    private final VBox container;

    public GprGriddingTool(RadarMap radarMap) {

        this.radarMap = radarMap;

        container = Tools.createToolContainer();
        getChildren().setAll(container);
    }

    @Override
    public boolean isVisibleFor(SgyFile file) {
        return file instanceof TraceFile;
    }

    @Override
    public void updateView() {
        super.updateView();

        container.getChildren().clear();
        if (selectedFile instanceof TraceFile traceFile) {
            container.getChildren().addAll(radarMap.getControlNodes(traceFile));
        }
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        Platform.runLater(() -> selectFile(event.getFile()));
    }
}

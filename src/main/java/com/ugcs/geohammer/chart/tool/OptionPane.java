package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.model.event.FileSelectedEvent;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.format.SgyFile;

import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

@Component
public class OptionPane extends VBox {

	private static final double WIDTH = 350;

    private final StackPane seriesControl;

    private final VBox toolContainer;

    public OptionPane(
            SeriesSelectorView seriesSelectorView,
            StatisticsTool statisticsTool,
            LowPassTool lowPassTool,
            GriddingTool griddingTool,
            TimeLagTool timeLagTool,
            RunningMedianTool runningMedianTool,
            QualityControlTool qualityControlTool,
            ScriptExecutionTool scriptExecutionTool,
            GprBackgroundTool gprBackgroundTool,
            GprGriddingTool gprGriddingTool,
            GprElevationTool gprElevationTool
    ) {
        setPadding(Insets.EMPTY);
        setMinWidth(0);
        setMaxWidth(WIDTH);
        setPrefWidth(WIDTH);

        seriesControl = new StackPane(seriesSelectorView);
        seriesControl.setPadding(new Insets(10, 16, 10, 16));
        seriesControl.setStyle("-fx-background-color: #666666;");

        toolContainer = new VBox(2 * Tools.DEFAULT_SPACING,
                new ToolToggleBox(statisticsTool, "Statistics"),
                new ToolToggleBox(lowPassTool, "Low-pass filter"),
                new ToolToggleBox(griddingTool, "Gridding"),
                new ToolToggleBox(timeLagTool, "GNSS time-lag"),
                new ToolToggleBox(runningMedianTool, "Running median filter"),
                new ToolToggleBox(gprBackgroundTool, "Background"),
                new ToolToggleBox(gprGriddingTool, "Gridding"),
                new ToolToggleBox(gprElevationTool, "Elevation"),
                new ToolToggleBox(qualityControlTool, "Quality control"),
                new ToolToggleBox(scriptExecutionTool, "Scripts")
        );
        toolContainer.setPadding(new Insets(10, 8, 10, 8));

        ScrollPane scrollContainer = Tools.createVerticalScrollContainer(toolContainer, this);
        getChildren().addAll(seriesControl, scrollContainer);
    }

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        SgyFile file = event.getFile();
        for (Node node : toolContainer.getChildren()) {
            if (node instanceof ToolToggleBox toggleBox) {
                ToolView tool = toggleBox.getTool();
                toggleBox.show(tool.isVisibleFor(file));
            }
        }
    }

    // gridding

    // TODO deprecated
    public void griddingProgress(boolean inProgress) {
//        Platform.runLater(() -> {
//            griddingView.showProgress(inProgress);
//            griddingView.disableInput(inProgress);
//            griddingView.disableActions(inProgress);
//        });
    }

    // TODO deprecated
    public GriddingRange getGriddingRange(CsvFile csvFile, String seriesName) {
        return new GriddingRange(0.0, 1.0, 0.0, 1.0);
        //return griddingView.getGriddingRange(csvFile, seriesName);
    }

    // TODO deprecated
    public ToggleButton getGridding() {
        //return griddingToggle;
        return new ToggleButton("Gridding");
    }

    static class ToolToggleBox extends VBox {

        private final ToolView tool;

        private final ToggleButton toggle;

        public ToolToggleBox(ToolView tool, String toggleText) {
            this.tool = tool;
            this.toggle = new ToggleButton(toggleText);

            toggle.setMaxWidth(Double.MAX_VALUE);
            toggle.setSelected(tool.isVisible());
            toggle.setOnAction(event -> tool.show(toggle.isSelected()));

            setSpacing(Tools.DEFAULT_SPACING);
            getChildren().setAll(toggle, tool);

            // hide by default
            show(false);
        }

        public ToolView getTool() {
            return tool;
        }

        public void show(boolean show) {
            setVisible(show);
            setManaged(show);
        }
    }
}

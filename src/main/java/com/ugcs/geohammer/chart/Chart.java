package com.ugcs.geohammer.chart;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.SelectedTrace;
import com.ugcs.geohammer.model.TraceUnit;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileCollapsedEvent;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Optional;

public abstract class Chart extends ScrollableData implements FileDataContainer {

    public static final int MIN_HEIGHT = 400;

    public static final int MARKER_OFFSET = 46;

    protected final Model model;

    private final VBox root;

    private final ChartHeader header;

    private @Nullable Region content;

    private volatile boolean collapsed;

    public Chart(Model model) {
        super(model);

        this.model = model;

        header = new ChartHeader();
        header.collapsedProperty().addListener(
                (observable, oldValue, newValue) -> onCollapsedChanged(newValue));
        header.getCloseButton().setOnAction(event -> close());
        root = new VBox(header);
        root.setPadding(new Insets(0, 0, 10, 0));
        root.setFillWidth(true);
        root.setOnMouseClicked(event -> model.selectChart(this));
    }

    protected void setContent(Region content) {
        Check.notNull(content);

        if (this.content != null) {
            root.getChildren().remove(this.content);
        }
        this.content = content;
        content.setVisible(!collapsed);
        content.setManaged(!collapsed);
        root.getChildren().add(content);
    }

    @Override
    public final VBox getRootNode() {
        return root;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    private void onCollapsedChanged(boolean collapsed) {
        this.collapsed = collapsed;

        if (content != null) {
            content.setVisible(!collapsed);
            content.setManaged(!collapsed);
        }
        getProfileScroll().setDisable(collapsed);

        repaint();
        model.publishEvent(new FileCollapsedEvent(this, getFile(), collapsed));
    }

    public void updateChartName() {
        SgyFile file = getFile();
        File source = file.getFile();
        String fileName = (file.isUnsaved() ? "*" : "") + (source != null ? source.getName() : "Noname");
        header.setName(fileName);
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

    public void close() {
        if (!confirmUnsavedChanges()) {
            return;
        }
        model.publishEvent(new FileClosedEvent(this, getFile()));
    }

    // releases chart resources when the chart is discarded
    public void dispose() {
    }

    public abstract SgyFile getFile();

    public abstract void init();

    // redraw
    public abstract void repaint();

    // reload data values to this chart
    public abstract void reload();

    // trace == null -> clear current selection
    public abstract void selectTrace(@Nullable SelectedTrace selectedTrace, boolean focus);

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

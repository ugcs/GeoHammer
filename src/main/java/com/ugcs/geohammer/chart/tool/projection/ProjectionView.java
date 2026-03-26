package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.control.InputWithLabel;
import com.ugcs.geohammer.chart.tool.projection.control.SelectorWithLabel;
import com.ugcs.geohammer.chart.tool.projection.model.GridOptions;
import com.ugcs.geohammer.chart.tool.projection.model.GridSamplingMethod;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionResult;
import com.ugcs.geohammer.chart.tool.projection.model.RenderOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSelection;
import com.ugcs.geohammer.chart.tool.projection.model.Viewport;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.Range;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.palette.SpectrumType;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Bindings;
import com.ugcs.geohammer.view.CanvasWindow;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Styles;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.chart.tool.projection.control.FoldableGroup;
import com.ugcs.geohammer.chart.tool.projection.control.SliderWithLabel;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.NumberStringConverter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectionView extends CanvasWindow {

    private final Model model;

    private final ProjectionModel projectionModel;

    private final ProjectionController projectionController;

    private @Nullable ProjectionRenderer renderer;

    // drag state

    private Point2D dragAnchor;

    private Point2D originAnchor;

	public ProjectionView(
            Model model,
            ProjectionModel projectionModel,
            ProjectionController projectionController) {
        super("Reprojection (experimental)", Styles.DARK_THEME_PATH);

        this.model = model;
        this.projectionModel = projectionModel;
        this.projectionController = projectionController;
	}

    private void initListeners() {
        // draw on viewport updates
        Viewport viewport = projectionModel.getViewport();
        Listeners.onChange(viewport.originProperty(), v -> draw());
        Listeners.onChange(viewport.scaleProperty(), v -> draw());

        // zoom on selection
        TraceSelection selection = projectionModel.getSelection();
        Listeners.onChange(selection.fileProperty(), v -> projectionController.requestZoomToProfile());
        Listeners.onChange(selection.lineProperty(), v -> projectionController.requestZoomToProfile());

        // draw on changing rendering options
        RenderOptions renderOptions = projectionModel.getRenderOptions();
        Listeners.onChange(renderOptions.showOriginsProperty(), v -> draw());
        Listeners.onChange(renderOptions.showTerrainProperty(), v -> draw());
        Listeners.onChange(renderOptions.showNormalsProperty(), v -> draw());
        Listeners.onChange(renderOptions.showGridProperty(), v -> draw());
        Listeners.onChange(renderOptions.maxGainProperty(), v -> draw());
        Listeners.onChange(renderOptions.contrastProperty(), v -> draw());
        Listeners.onChange(renderOptions.spectrumTypeProperty(), v -> draw());

        // drow on result updates
        ProjectionResult projectionResult = projectionModel.getResult();
        Listeners.onChange(projectionResult.profileProperty(), v -> {
            zoomToProfile();
            draw();
        });
        Listeners.onChange(projectionResult.gridProperty(), v -> draw());
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        if (canvas != null) {
            renderer = new ProjectionRenderer(projectionModel, canvas);
        }

        if (root != null) {
            Node toolBar = createToolBar();
            StackPane.setAlignment(toolBar, Pos.TOP_RIGHT);
            StackPane.setMargin(toolBar, new Insets(16));

            root.getChildren().add(toolBar);

            initMouseHandlers(root);
            initListeners();
        }
    }

    private Node createToolBar() {
        VBox toolBar = new VBox(8,
                createSelectionGroup(),
                createRenderGroup(),
                createProjectionGroup(),
                createGridGroup()
        );
        toolBar.getStyleClass().add("tool-panel");
        toolBar.setAlignment(Pos.TOP_LEFT);
        toolBar.setPrefWidth(240);
        toolBar.setMaxWidth(240);
        toolBar.setMaxHeight(VBox.USE_PREF_SIZE);
        return toolBar;
    }

    private Node createSelectionGroup() {
        TraceSelection selection = projectionModel.getSelection();

        SelectorWithLabel<Integer> line = new SelectorWithLabel<>("Line", List.of());
        line.getSelector().setItems(selection.getFileLines());
        line.getSelector().valueProperty().bindBidirectional(selection.lineProperty());
        HBox.setHgrow(line, Priority.ALWAYS);

        Button fitButton = new Button("Fit");
        fitButton.setOnAction(e -> {
            projectionController.requestZoomToProfile();
            zoomToProfile();
            draw();
        });
        fitButton.setMinWidth(40);

        return new HBox(line, fitButton);
    }

    private Node createProjectionGroup() {
        ProjectionOptions projectionOptions = projectionModel.getProjectionOptions();

        InputWithLabel zeroSample = new InputWithLabel("Zero ns sample");
        zeroSample.getInput().textProperty().bindBidirectional(
                projectionOptions.sampleOffsetProperty(), new NumberStringConverter());

        InputWithLabel relativePermittivity = new InputWithLabel("Relative permittivity εr");
        relativePermittivity.getInput().textProperty().bindBidirectional(
                projectionOptions.relativePermittivityProperty(), new NumberStringConverter());

        SliderWithLabel antennaOffset = new SliderWithLabel("Antenna offset", "cm", new Range(-300, 300));
        antennaOffset.getSlider().valueProperty().bindBidirectional(
                Bindings.metersToCentimeters(projectionOptions.antennaOffsetProperty()));

        SliderWithLabel terrainOffset = new SliderWithLabel("Terrain offset", "cm", new Range(-500, 500));
        terrainOffset.getSlider().valueProperty().bindBidirectional(
                Bindings.metersToCentimeters(projectionOptions.terrainOffsetProperty()));

        SliderWithLabel antennaSmoothing = new SliderWithLabel("Antenna smoothing", "cm", new Range(0, 500));
        antennaSmoothing.getSlider().valueProperty().bindBidirectional(
                Bindings.metersToCentimeters(projectionOptions.antennaSmoothingRadiusProperty()));

        SliderWithLabel terrainSmoothing = new SliderWithLabel("Terrain smoothing", "cm", new Range(0, 1000));
        terrainSmoothing.getSlider().valueProperty().bindBidirectional(
                Bindings.metersToCentimeters(projectionOptions.terrainSmoothingRadiusProperty()));

        CheckBox diffuseNormals = new CheckBox("Diffuse normals");
        diffuseNormals.selectedProperty().bindBidirectional(projectionOptions.diffuseNormalsProperty());

        return new FoldableGroup(
                "Projection",
                relativePermittivity,
                zeroSample,
                antennaOffset,
                terrainOffset,
                antennaSmoothing,
                terrainSmoothing,
                diffuseNormals
        );
    }

    private Node createGridGroup() {
        GridOptions gridOptions = projectionModel.getGridOptions();

        SelectorWithLabel<GridSamplingMethod> gridSampling = new SelectorWithLabel<>("Sampling", List.of(GridSamplingMethod.values()));
        gridSampling.getSelector().valueProperty().bindBidirectional(gridOptions.samplingMethodProperty());

        SliderWithLabel cellWidth = new SliderWithLabel("Cell width", "cm", new Range(0.5, 30));
        cellWidth.getSlider().valueProperty().bindBidirectional(
                Bindings.metersToCentimeters(gridOptions.cellWidthProperty()));

        SliderWithLabel cellHeight = new SliderWithLabel("Cell height", "cm", new Range(0.5, 30));
        cellHeight.getSlider().valueProperty().bindBidirectional(
                Bindings.metersToCentimeters(gridOptions.cellHeightProperty()));

        ProjectionResult result = projectionModel.getResult();
        Label gridDetails = new Label();
        Listeners.onChange(result.gridProperty(), grid -> {
            String text = grid != null
                    ? "Grid " + grid.getWidth() + " ✕ " + grid.getHeight()
                    : Strings.empty();
            gridDetails.setText(text);
        });

        Button autoSizeButton = new Button("Auto-size");
        autoSizeButton.setOnAction(e -> {
            projectionController.autoSizeGridCell();
        });
        autoSizeButton.setMinWidth(50);

        HBox statusRow = new HBox(gridDetails, Views.createSpacer(), autoSizeButton);
        statusRow.setAlignment(Pos.BASELINE_LEFT);

        return new FoldableGroup(
                "Grid",
                gridSampling,
                cellWidth,
                cellHeight,
                statusRow
        );
    }

    private Node createRenderGroup() {
        RenderOptions renderOptions = projectionModel.getRenderOptions();
        GridOptions gridOptions = projectionModel.getGridOptions();

        CheckBox showOrigins = new CheckBox("Origins");
        showOrigins.selectedProperty().bindBidirectional(renderOptions.showOriginsProperty());

        CheckBox showTerrain = new CheckBox("Terrain");
        showTerrain.selectedProperty().bindBidirectional(renderOptions.showTerrainProperty());

        CheckBox showNormals = new CheckBox("Normals");
        showNormals.selectedProperty().bindBidirectional(renderOptions.showNormalsProperty());

        CheckBox showGrid = new CheckBox("Grid");
        showGrid.selectedProperty().bindBidirectional(renderOptions.showGridProperty());

        CheckBox interpolateGrid = new CheckBox("Interpolate grid");
        interpolateGrid.selectedProperty().bindBidirectional(gridOptions.interpolateGridProperty());

        CheckBox cropAir = new CheckBox("Crop air");
        cropAir.selectedProperty().bindBidirectional(gridOptions.cropAirProperty());

        CheckBox removeBackground = new CheckBox("Remove background");
        removeBackground.selectedProperty().bindBidirectional(renderOptions.removeBackgroundProperty());

        SliderWithLabel gain = new SliderWithLabel("Gain at max depth", "dB", new Range(0, 128));
        gain.getSlider().valueProperty().bindBidirectional(renderOptions.maxGainProperty());

        SliderWithLabel contrast = new SliderWithLabel("Contrast", "", new Range(0, 100));
        contrast.getSlider().valueProperty().bindBidirectional(renderOptions.contrastProperty());

        SelectorWithLabel<SpectrumType> spectrum = new SelectorWithLabel<>("Palette", List.of(SpectrumType.values()));
        spectrum.getSelector().valueProperty().bindBidirectional(renderOptions.spectrumTypeProperty());

        GridPane checkboxGrid = new GridPane();
        ColumnConstraints columnConstraints = new ColumnConstraints();
        columnConstraints.setPercentWidth(50);
        checkboxGrid.getColumnConstraints().addAll(
                columnConstraints,
                columnConstraints
        );
        checkboxGrid.addRow(0, showOrigins, showGrid);
        checkboxGrid.addRow(1, showTerrain, interpolateGrid);
        checkboxGrid.addRow(2, showNormals, cropAir);

        return new FoldableGroup(
                "View",
                checkboxGrid,
                removeBackground,
                gain,
                contrast,
                spectrum
        );
    }

    private void initMouseHandlers(StackPane pane) {
        Viewport viewport = projectionModel.getViewport();
        pane.setOnMousePressed(event -> {
            dragAnchor = new Point2D(event.getX(), event.getY());
            originAnchor = viewport.getOrigin();
        });
        pane.setOnMouseDragged(event -> {
            if (dragAnchor != null && originAnchor != null) {
                double dx = event.getX() - dragAnchor.getX();
                double dy = event.getY() - dragAnchor.getY();

                Point2D scale = viewport.getScale();
                viewport.originProperty().set(new Point2D(
                        originAnchor.getX() - dx * scale.getX(),
                        originAnchor.getY() - dy * scale.getY()
                ));
            }
        });
        pane.setOnScroll(event -> {
            // macOS swaps deltaY -> deltaX when shift is held
            double delta = event.isShiftDown() ? event.getDeltaX() : event.getDeltaY();
            double factor = delta > 0 ? 0.9 : 1.1;
            double kx = factor;
            double ky = event.isShiftDown() ? 1.0 : factor;
            Point2D worldPoint = viewport.toWorld(
                    new Point2D(event.getX(), event.getY()));
            viewport.zoom(worldPoint, kx, ky);
        });
    }

    @Override
    protected void onShow() {
        super.onShow();

        TraceFile file = null;
        if (model.getCurrentFile() instanceof TraceFile traceFile) {
            file = traceFile;
        }
        projectionController.selectFile(file);
        draw();
    }

    @Override
    protected void onDraw() {
        super.onDraw();
        draw();
    }

    private void zoomToProfile() {
        if (canvas != null) {
            TraceProfile traceProfile = projectionModel.getResult().getProfile();
            Viewport viewport = projectionModel.getViewport();
            if (traceProfile != null && viewport.isZoomToProfile()) {
                viewport.zoomToProfileProperty().set(false);
                // zoom
                viewport.fit(traceProfile.getEnvelope(),
                        canvas.getWidth(),
                        canvas.getHeight());
            }
        }
    }

	private void draw() {
        if (isShowing() && renderer != null) {
            renderer.draw();
        }
	}

    @EventListener
    private void onFileSelected(FileSelectedEvent event) {
        if (!isShowing()) {
            return;
        }
        if (event.getFile() == null) {
            projectionController.selectFile(null);
        }
        if (event.getFile() instanceof TraceFile file) {
            projectionController.selectFile(file);
        }
    }

    @EventListener
    private void somethingChanged(WhatChanged changed) {
        if (!isShowing()) {
            return;
        }
        if (changed.isTraceCut()) {
            projectionController.updateLines();
            projectionController.updateTraceProfile();
        }
    }
}

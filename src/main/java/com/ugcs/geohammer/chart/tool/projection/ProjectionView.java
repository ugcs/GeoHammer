package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.control.FoldableGroup;
import com.ugcs.geohammer.chart.tool.projection.control.InputWithLabel;
import com.ugcs.geohammer.chart.tool.projection.control.SelectorWithLabel;
import com.ugcs.geohammer.chart.tool.projection.control.SliderWithLabel;
import com.ugcs.geohammer.chart.tool.projection.model.Axis;
import com.ugcs.geohammer.chart.tool.projection.model.GridOptions;
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
import com.ugcs.geohammer.util.Text;
import com.ugcs.geohammer.view.Bindings;
import com.ugcs.geohammer.view.CanvasWindow;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.WindowProperties;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
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

    private @Nullable ContextMenu contextMenu;

    // drag state

    private Point2D dragAnchor;

    private Point2D originAnchor;

	public ProjectionView(
            Model model,
            ProjectionModel projectionModel,
            ProjectionController projectionController) {
        super(getWindowProperties());

        this.model = model;
        this.projectionModel = projectionModel;
        this.projectionController = projectionController;
	}

    private static WindowProperties getWindowProperties() {
        return new WindowProperties("Reprojection (experimental)")
                .withStyle(StageStyle.DECORATED)
                .withSize(1140, 760)
                .withMinSize(600, 400);
    }

    private void initListeners() {
        // draw on viewport updates
        Viewport viewport = projectionModel.getViewport();
        Listeners.onChange(viewport.originProperty(), v -> draw());
        Listeners.onChange(viewport.scaleProperty(), v -> draw());

        // draw on axis updates
        Axis axis = projectionModel.getAxis();
        Listeners.onChange(axis.originProperty(), v -> draw());

        // zoom on selection
        TraceSelection selection = projectionModel.getSelection();
        Listeners.onChange(selection.fileProperty(), v -> projectionController.requestZoomToProfile());
        Listeners.onChange(selection.lineProperty(), v -> projectionController.requestZoomToProfile());

        // draw on changing rendering options
        RenderOptions renderOptions = projectionModel.getRenderOptions();
        Listeners.onChange(renderOptions.showOriginsProperty(), v -> draw());
        Listeners.onChange(renderOptions.showTerrainProperty(), v -> draw());
        Listeners.onChange(renderOptions.showNormalsProperty(), v -> draw());
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
            Node toolPane = createToolPane(root);
            StackPane.setAlignment(toolPane, Pos.TOP_RIGHT);
            StackPane.setMargin(toolPane, new Insets(16, 16, 48, 16));

            root.getChildren().addAll(toolPane);

            initMouseHandlers(root);
            initContextMenu(root);
            initListeners();
        }
    }

    private Node createToolPane(Node parent) {
        VBox toolPane = new VBox(8,
                createSelectGroup(),
                createGridGroup(),
                createRenderGroup(),
                createProjectionGroup(),
                createAdvancedGroup()
        );
        toolPane.setAlignment(Pos.TOP_LEFT);

        ScrollPane scrollContainer = Views.createVerticalScrollContainer(toolPane, parent);
        scrollContainer.getStyleClass().add("surface-translucent");
        scrollContainer.setPrefWidth(240);
        scrollContainer.setMaxWidth(240);
        scrollContainer.setMaxHeight(VBox.USE_PREF_SIZE);

        scrollContainer.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (scrollContainer.getVvalue() == 0 && event.getDeltaY() > 0) {
                event.consume();
            } else if (scrollContainer.getVvalue() == 1.0 && event.getDeltaY() < 0) {
                event.consume();
            }
        });

        return scrollContainer;
    }

    private Node createSelectGroup() {
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

        return new HBox(8, line, fitButton);
    }

    private Node createGridGroup() {
        GridOptions gridOptions = projectionModel.getGridOptions();

        SliderWithLabel resolution = new SliderWithLabel("Resolution", "%", new Range(1, 100));
        resolution.getSlider().valueProperty().bindBidirectional(
                Bindings.fractionToPercent(gridOptions.resolutionProperty()));

        CheckBox autoUpdateGrid = new CheckBox("Auto-update");
        autoUpdateGrid.selectedProperty().bindBidirectional(gridOptions.autoUpdateProperty());

        Button gridButton = new Button("Grid");
        gridButton.setOnAction(e -> {
            projectionController.buildGrid();
        });
        gridButton.setMinWidth(50);

        HBox gridActionGroup = new HBox(8, autoUpdateGrid, Views.createSpacer(), gridButton);
        gridActionGroup.setAlignment(Pos.BASELINE_LEFT);

        ProgressBar gridProgress = new ProgressBar();
        gridProgress.setMaxWidth(Double.MAX_VALUE);
        gridProgress.setPrefHeight(3);
        gridProgress.progressProperty().bind(gridOptions.gridProgressProperty());
        gridProgress.visibleProperty().bind(
                gridOptions.gridProgressProperty().greaterThan(0)
                        .and(gridOptions.gridProgressProperty().lessThan(1)));

        VBox group = new VBox(
                8,
                resolution,
                gridActionGroup,
                gridProgress
        );
        group.getStyleClass().add("group");
        return group;
    }

    private Node createRenderGroup() {
        RenderOptions renderOptions = projectionModel.getRenderOptions();

        SelectorWithLabel<SpectrumType> spectrum = new SelectorWithLabel<>("Palette", List.of(SpectrumType.values()));
        spectrum.getSelector().valueProperty().bindBidirectional(renderOptions.spectrumTypeProperty());

        SliderWithLabel gain = new SliderWithLabel("Gain at max depth", "dB", new Range(0, 128));
        gain.getSlider().valueProperty().bindBidirectional(renderOptions.maxGainProperty());

        SliderWithLabel contrast = new SliderWithLabel("Contrast", "%", new Range(0, 100));
        contrast.getSlider().valueProperty().bindBidirectional(
                Bindings.fractionToPercent(renderOptions.contrastProperty()));

        VBox group = new VBox(
                8,
                spectrum,
                gain,
                contrast
        );
        group.getStyleClass().add("group");
        return group;
    }

    private Node createProjectionGroup() {
        ProjectionOptions projectionOptions = projectionModel.getProjectionOptions();

        InputWithLabel zeroSample = new InputWithLabel("Zero ns sample");
        zeroSample.getInput().textProperty().bindBidirectional(
                projectionOptions.sampleOffsetProperty(), new NumberStringConverter());

        InputWithLabel centerFrequency = new InputWithLabel("Center frequency, MHz");
        centerFrequency.getInput().textProperty().bindBidirectional(
                projectionOptions.centerFrequencyProperty(), new NumberStringConverter());

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

        SliderWithLabel normalWeight = new SliderWithLabel("Normal weight", "", new Range(0, 1));
        normalWeight.getSlider().valueProperty().bindBidirectional(
                projectionOptions.normalWeightProperty());

        return new FoldableGroup(
                "Projection",
                zeroSample,
                centerFrequency,
                relativePermittivity,
                antennaOffset,
                terrainOffset,
                antennaSmoothing,
                terrainSmoothing,
                normalWeight
        );
    }

    private Node createAdvancedGroup() {
        RenderOptions renderOptions = projectionModel.getRenderOptions();
        GridOptions gridOptions = projectionModel.getGridOptions();

        CheckBox showOrigins = new CheckBox("Show trace origins");
        showOrigins.selectedProperty().bindBidirectional(renderOptions.showOriginsProperty());

        CheckBox showTerrain = new CheckBox("Show terrain");
        showTerrain.selectedProperty().bindBidirectional(renderOptions.showTerrainProperty());

        CheckBox showNormals = new CheckBox("Show trace normals");
        showNormals.selectedProperty().bindBidirectional(renderOptions.showNormalsProperty());

        CheckBox interpolateGrid = new CheckBox("Interpolate grid");
        interpolateGrid.selectedProperty().bindBidirectional(gridOptions.interpolateGridProperty());

        CheckBox removeBackground = new CheckBox("Remove background");
        removeBackground.selectedProperty().bindBidirectional(renderOptions.removeBackgroundProperty());

        CheckBox cropAir = new CheckBox("Crop air gap");
        cropAir.selectedProperty().bindBidirectional(gridOptions.cropAirProperty());

        CheckBox migration = new CheckBox("Migration (sector summation)");
        migration.selectedProperty().bindBidirectional(gridOptions.migrationProperty());

        CheckBox refraction = new CheckBox("Refraction");
        refraction.selectedProperty().bindBidirectional(gridOptions.refractionProperty());

        SliderWithLabel fresnelApertureFactor = new SliderWithLabel("Fresnel aperture factor", "", new Range(1, 3));
        fresnelApertureFactor.getSlider().valueProperty().bindBidirectional(
                gridOptions.fresnelApertureFactorProperty());

        FoldableGroup group = new FoldableGroup(
                "Advanced",
                showOrigins,
                showTerrain,
                showNormals,
                interpolateGrid,
                removeBackground,
                cropAir,
                migration,
                refraction,
                fresnelApertureFactor
        );
        group.setFolded(true);
        return group;
    }

    private void initContextMenu(StackPane pane) {
        pane.setOnMouseReleased(event -> {
            if (event.getButton() != MouseButton.SECONDARY || !event.isStillSincePress()) {
                return;
            }
            if (contextMenu != null) {
                contextMenu.hide();
            }
            Viewport viewport = projectionModel.getViewport();
            Axis axis = projectionModel.getAxis();
            Point2D world = viewport.toWorld(new Point2D(event.getX(), event.getY()));
            Point2D display = world.subtract(axis.getOrigin());

            MenuItem setAxisOrigin = new MenuItem("Set axis origin to (x: "
                    + Text.formatNumber(display.getX(), 2)
                    + ", y:"
                    + Text.formatNumber(display.getY(), 2)
                    + ")");
            setAxisOrigin.setOnAction(e -> axis.originProperty().set(world));

            MenuItem resetAxisOrigin = new MenuItem("Reset axis origin");
            resetAxisOrigin.setOnAction(e -> axis.originProperty().set(Point2D.ZERO));

            contextMenu = new ContextMenu(
                    setAxisOrigin,
                    resetAxisOrigin);
            contextMenu.show(pane, event.getScreenX(), event.getScreenY());
        });
    }

    private void initMouseHandlers(StackPane pane) {
        Viewport viewport = projectionModel.getViewport();
        pane.setOnMousePressed(event -> {
            if (contextMenu != null) {
                contextMenu.hide();
            }
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
    protected void onHide() {
        super.onHide();

        projectionController.selectFile(null);
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

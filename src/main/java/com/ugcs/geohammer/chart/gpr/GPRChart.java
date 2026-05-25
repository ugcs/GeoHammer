package com.ugcs.geohammer.chart.gpr;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.ProfileView;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.chart.gpr.axis.HorizontalRulerController;
import com.ugcs.geohammer.chart.gpr.axis.HorizontalRulerDrawer;
import com.ugcs.geohammer.chart.gpr.axis.LeftRulerController;
import com.ugcs.geohammer.chart.gpr.axis.VerticalRulerDrawer;
import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.model.TraceUnit;
import com.ugcs.geohammer.view.Colors;
import com.ugcs.geohammer.view.PaintLimiter;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.SelectedTrace;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.TraceSample;
import com.ugcs.geohammer.model.element.AuxElementEditHandler;
import com.ugcs.geohammer.model.element.CleverViewScrollHandler;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.ClickPlace;
import com.ugcs.geohammer.model.element.CloseGprChartButton;
import com.ugcs.geohammer.model.element.DepthHeight;
import com.ugcs.geohammer.model.element.DepthStart;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.element.RemoveLineButton;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.element.ShapeHolder;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.format.HorizontalProfile;
import com.ugcs.geohammer.view.BaseSlider;
import com.ugcs.geohammer.model.IndexRange;
import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.MouseDragEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jfree.fx.FXGraphics2D;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;

public class GPRChart extends Chart {

    private static final Stroke AMP_STROKE = new BasicStroke(1.0f);

    private static final Stroke LEVEL_STROKE = new BasicStroke(1.0f);

    private static final Font PLAIN_FONT = new Font("Verdana", Font.PLAIN, 8);

    private static final Font BOLD_FONT = new Font("Verdana", Font.BOLD, 8);

    private static final BasicStroke DASHED_STROKE =
            new BasicStroke(1.0f,
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, new float[] {5.0f}, 0.0f);

	private static final int MIN_CONTRAST = 0;

    private static final int MAX_CONTRAST = 100;

    private static final String DEPTH_SLIDER_TOOLTIP = "Hold Ctrl to move independently";

	private BaseObject selectedMouseHandler;

	private boolean dragCtrlDown;

    private final BaseObject scrollHandler;

    private final Model model;

    private final AuxElementEditHandler auxEditHandler;

    private BufferedImage drawImage;

    private int width = 800;

    private int height = 600;

    private final PaintLimiter repaintLimiter = new PaintLimiter(60, () -> draw(width, height));

    private double contrast = 50;

    private final VBox vbox = new VBox();

    private final Canvas canvas = new Canvas(width, height);

    private final GraphicsContext gc = canvas.getGraphicsContext2D();

    private final FXGraphics2D g2 = new FXGraphics2D(gc);

    private final PrismDrawer prismDrawer;

    private final ContrastSlider contrastSlider;

    private final MutableInt shiftGround = new MutableInt(0);

    private final LeftRulerController leftRulerController;

    private final HorizontalRulerController horizontalRulerController;

    private final ProfileField profileField;

    private final List<BaseObject> auxElements = new ArrayList<>();

    private final VerticalRulerDrawer verticalRulerDrawer = new VerticalRulerDrawer(this);

    private final HorizontalRulerDrawer horizontalRulerDrawer = new HorizontalRulerDrawer(this);

    public GPRChart(Model model, TraceFile traceFile) {
        super(model);
        this.model = model;
        this.auxEditHandler = model.getAuxEditHandler();
        this.profileField = new ProfileField(traceFile);
        this.leftRulerController = new LeftRulerController(profileField);
        this.horizontalRulerController = new HorizontalRulerController(model, traceFile);

        vbox.getChildren().addAll(canvas);
        vbox.setOnMouseClicked(event -> {
            select();
        });
        prismDrawer = new PrismDrawer(model);
        initCanvas();

        ChangeListener<Number> contrastListener = (observable, oldValue, newValue) -> {
            repaintEvent();
            setContrastToMeta();
        };
        contrastSlider = new ContrastSlider(profileField.getProfileSettings(), contrastListener);

		setContrastFromMeta(contrastSlider, traceFile);
		setDepthRangeFromMeta(traceFile);

        getProfileScroll().setChangeListener(new ChangeListener<Number>() {
            public void changed(ObservableValue<? extends Number> ov, Number oldVal, Number newVal) {
                    repaintEvent();
            }
        });

        scrollHandler = new CleverViewScrollHandler();
        updateAuxElements();
    }

    public void close() {
        if (!confirmUnsavedChanges()) {
            return;
        }
        repaintLimiter.stop();
        model.publishEvent(new FileClosedEvent(this, getFile()));
    }

    @Override
    public void init() {
        vbox.setPrefHeight(Math.max(Chart.MIN_HEIGHT, vbox.getScene().getHeight()));
        vbox.setMinHeight(Math.max(Chart.MIN_HEIGHT, vbox.getScene().getHeight() / 2));

        ProfileView profileView = AppContext.getInstance(ProfileView.class);
        setSize(
                (int)(profileView.getCenter().getWidth() - 21),
                (int)(Math.max(400, vbox.getHeight()) - 6));
        fitFull();
    }

    @Override
    public void repaint() {
        repaintLimiter.requestPaint();
    }

    public void repaintEvent() {
        if (!model.isLoading() && profileField.getGprTracesCount() > 0) {
            Platform.runLater(this::repaint);
        }
    }

    @Override
    public void reload() {
        profileField.updateMaxHeightInSamples();
    }

    public BaseSlider getContrastSlider() {
        return contrastSlider;
    }

    public void setSize(int width, int height) {
        Rectangle mainRect = profileField.getMainRect();
        double viewWidthBefore = mainRect.getWidth();
        double viewHeightBefore = mainRect.getHeight();

        this.width = width;
        this.height = height;

        profileField.setViewDimension(new Dimension(this.width, this.height));
        rescaleZoom(viewWidthBefore, viewHeightBefore);
        repaintEvent();
    }

    public List<BaseObject> getAuxElements() {
        return auxElements;
    }

	private void setContrastToMeta() {
		TraceFile traceFile = profileField.getFile();
		MetaFile meta = traceFile.getMetaFile();
		if (meta != null) {
			meta.setContrast(contrast);
		}
	}

	public void applyDepthRange(IndexRange depthRange) {
		int max = profileField.getMaxHeightInSamples();
		if (max <= 0) {
			return;
		}
		var settings = profileField.getProfileSettings();
		int clampedLayer = Math.min(Math.max(0, depthRange.from()), max - 1);
		int clampedHpage = Math.min(Math.max(0, depthRange.to() - depthRange.from()), max - clampedLayer);
		settings.setLayer(clampedLayer);
		settings.hpage = clampedHpage;
		repaintEvent();
		updateDepthRangeInMeta();
	}

	private void updateDepthRangeInMeta() {
		TraceFile traceFile = profileField.getFile();
		MetaFile meta = traceFile.getMetaFile();
		if (meta != null) {
			var profileSettings = profileField.getProfileSettings();
			int min = profileSettings.getLayer();
			int max = min + profileSettings.hpage;
			meta.setDepthRange(new IndexRange(min, max));
		}
	}

	private void setContrastFromMeta(ContrastSlider slider, TraceFile traceFile) {
		MetaFile meta = traceFile.getMetaFile();
		Double contrastFromMeta = meta != null ? meta.getContrast() : null;
		if (slider != null && contrastFromMeta != null) {
			this.contrast = Math.clamp(contrastFromMeta, MIN_CONTRAST, MAX_CONTRAST);
			slider.updateUI();
		}
	}

	private void setDepthRangeFromMeta(TraceFile traceFile) {
		MetaFile meta = traceFile.getMetaFile();
		IndexRange savedRange = meta != null ? meta.getDepthRange() : null;
		if (savedRange != null) {
			var profileSettings = profileField.getProfileSettings();
			profileSettings.hpage = savedRange.to() - savedRange.from();
			profileSettings.setLayer(savedRange.from());
		}
	}

    private class ContrastSlider extends BaseSlider {
        public ContrastSlider(Settings settings, ChangeListener<Number> listenerExt) {
            super(settings, listenerExt);
            name = "Contrast";
            units = "";
            tickUnits = 25;
        }

        public void updateUI() {
			if (slider == null) {
				return;
			}
            slider.setMax(MAX_CONTRAST);
            slider.setMin(MIN_CONTRAST);
            slider.setValue(contrast);
        }

        public int updateModel() {
            contrast = (int) slider.getValue();
			setContrastToMeta();
            return (int) contrast;
        }
    }

    private void initCanvas() {
        canvas.setOnScroll(event -> {
            double x = event.getSceneX();
            double y = event.getSceneY();
            double factor = (event.getDeltaY() > 0 ? ZOOM_IN : ZOOM_OUT);
            zoom(x, y, factor, event.isControlDown());

            event.consume(); // don't scroll the page
        });

        canvas.setOnMousePressed(mousePressHandler);
        canvas.setOnMouseReleased(mouseReleaseHandler);
        canvas.setOnMouseMoved(mouseMoveHandler);
        canvas.setOnMouseExited(event -> auxEditHandler.mouseExitHandle(GPRChart.this));
        canvas.setOnMouseClicked(mouseClickHandler);
        canvas.addEventFilter(MouseEvent.DRAG_DETECTED, dragDetectedHandler);
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseMoveHandler);
        canvas.addEventFilter(MouseDragEvent.MOUSE_DRAG_RELEASED, dragReleaseHandler);
    }

    void zoom(double factor) {
        double x = 0.5 * width;
        double y = 0.5 * height;
        zoom(x, y, factor, false);
    }

    private void zoom(double x, double y, double factor, boolean horizontalOnly) {
        TraceSample before = screenToTraceSample(toLocalPoint(x, y));

        setHorizontalScale(factor * getHorizontalScale());
        if (!horizontalOnly) {
            setVerticalScale(factor * getVerticalScale());
        }

        TraceSample after = screenToTraceSample(toLocalPoint(x, y));
        setStartTrace(getStartTrace() - (after.trace() - before.trace()));
        setStartSample(getStartSample() - (after.sample() - before.sample()));

        updateScroll();
        repaintEvent();
    }

    @Override
    public void setStartTrace(int startTrace) {
        int lineIndexBefore = getSelectedLineIndex();

        super.setStartTrace(startTrace);

        int lineIndexAfter = getSelectedLineIndex();
        if (lineIndexBefore != lineIndexAfter) {
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
        }
    }

    public void updateScroll() {
        if (!model.isActive() || profileField.getGprTracesCount() == 0) {
            return;
        }
        getProfileScroll().syncFromScrollable();
    }

    private void select() {
        model.selectChart(this);
    }

    @Override
    public Node getRootNode() {
        return vbox;
    }

    public Canvas getCanvas() {
        return canvas;
    }

    public Point2D localToCanvas(Point2D localPoint) {
        var mainRect = profileField.getMainRect();
        return new Point2D(
                localPoint.getX() + mainRect.x + mainRect.width / 2.0,
                localPoint.getY());
    }

    @Override
    public void selectFile() {
        model.publishEvent(new FileSelectedEvent(this, profileField.getFile()));
    }

    private void draw(int width, int height) {
        if (width <= 0 || height <= 0 || !model.isActive() || profileField.getGprTracesCount() == 0) {
            return;
        }

        Color strokeColor = Colors.awtColor(AppContext.getTheme().strokeColor());

        if (!(canvas.getWidth() == width && canvas.getHeight() == height)) {
            canvas.setWidth(width);
            canvas.setHeight(height);
        }

        if (drawImage == null
                || drawImage.getWidth() != width
                || drawImage.getHeight() != height) {
            drawImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        int[] buffer = ((DataBufferInt) drawImage.getRaster().getDataBuffer()).getData();
        Arrays.fill(buffer, Colors.AWT_TRANSPARENT.getRGB());

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setClip(null);
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawAxis(g2, strokeColor);

        verticalRulerDrawer.draw(g2, strokeColor);
        horizontalRulerDrawer.draw(g2, strokeColor);

        var mainRect = profileField.getMainRect();
        g2.setClip(mainRect.x, mainRect.y, mainRect.width, mainRect.height);
        prismDrawer.draw(width, this, g2, buffer, getRealContrast());
        g2.drawImage(drawImage, 0, 0, width, height, null);

        g2.translate(mainRect.x + mainRect.width / 2, 0);

        drawAuxGraphics1(g2);
        drawAuxElements(g2);

        var clipTopMainRect = profileField.getClipTopMainRect();
        g2.setClip(clipTopMainRect.x,
                clipTopMainRect.y,
                clipTopMainRect.width,
                clipTopMainRect.height);

        drawFileName(g2, strokeColor);
        drawLines(g2);

        g2.translate(-(mainRect.x + mainRect.width / 2), 0);
    }

    private void drawAuxGraphics1(Graphics2D g2) {
        Rectangle r = profileField.getClipMainRect();
        g2.setClip(r.x, r.y, r.width, r.height);

        drawFileProfiles(g2);
        drawAmplitudeMapLevels(g2);
    }

    private void drawFileProfiles(Graphics2D graphicsContext) {
        TraceFile file = profileField.getFile();
        // ground
        if (file.getGroundProfile() != null) {
            graphicsContext.setStroke(LEVEL_STROKE);
            drawHorizontalProfile(graphicsContext,
                    file.getGroundProfile(),
                    shiftGround.intValue());
        }
    }

    private double getRealContrast() {
        return Math.pow(1.08, 140 - contrast);
    }

    private void drawAmplitudeMapLevels(Graphics2D g2) {
        g2.setColor(Color.MAGENTA);
        g2.setStroke(DASHED_STROKE);

        var profileSettings = profileField.getProfileSettings();

        int y = sampleToScreen(profileSettings.getLayer());
        g2.drawLine(-width / 2, y, width / 2, y);

        int bottomSelectedSmp = profileSettings.getLayer() + profileSettings.hpage;
        int y2 = sampleToScreen(bottomSelectedSmp);


        g2.drawLine(-width / 2, y2, width / 2, y2);
    }

    private void drawAuxElements(Graphics2D g2) {
        boolean full = true; //!controller.isEnquiued();

        for (BaseObject bo : auxElements) {
            if (full || bo.isSelected()) {
                bo.drawOnCut(g2, this);
            }
        }

        for (SelectedTrace selected : model.getSelectedTraces()) {
            if (!Objects.equals(selected.trace().getFile(), getFile())) {
                continue;
            }
            ClickPlace clickPlace = new ClickPlace(selected);
            clickPlace.drawOnCut(g2, this);
        }
    }

    public void updateAuxElements() {
        auxElements.clear();

        TraceFile file = profileField.getFile();
        auxElements.addAll(file.getAuxElements());

        // line removal buttons
        NavigableMap<Integer, IndexRange> lineRanges = getFile().getLineRanges();
        for (IndexRange range : lineRanges.values()) {
            RemoveLineButton removeLine = new RemoveLineButton(
                    new TraceKey(file, range.from()),
                    model);
            auxElements.add(removeLine);
        }

        auxElements.add(new DepthStart(ShapeHolder.topSelection, new Tooltip(DEPTH_SLIDER_TOOLTIP)));
        auxElements.add(new DepthHeight(ShapeHolder.botSelection, new Tooltip(DEPTH_SLIDER_TOOLTIP)));
        auxElements.add(leftRulerController.getTB());
        auxElements.add(horizontalRulerController.getTB());

        // close button
        auxElements.add(new CloseGprChartButton(
                new TraceKey(file, 0), model));
    }

    public LeftRulerController getLeftRulerController() {
        return leftRulerController;
    }

    public HorizontalRulerController getHorizontalRulerController() {
        return horizontalRulerController;
    }

    @Override
    public void setTraceUnit(TraceUnit traceUnit) {
        horizontalRulerController.setUnit(traceUnit);
    }

    private void drawAxis(Graphics2D g2, Color strokeColor) {
        Rectangle topRuleRect = profileField.getTopRuleRect();
        Rectangle leftRuleRect = profileField.getLeftRuleRect();
        Rectangle bottomRuleRect = profileField.getBottomRuleRect();

        g2.setPaint(Colors.opaque(strokeColor, 0.25f));
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(topRuleRect.x,
                topRuleRect.y + topRuleRect.height + 1,
                topRuleRect.x + topRuleRect.width,
                topRuleRect.y + topRuleRect.height + 1);

        g2.drawLine(topRuleRect.x,
                topRuleRect.y + topRuleRect.height + 1,
                topRuleRect.x,
				leftRuleRect.y + leftRuleRect.height - bottomRuleRect.height);

        g2.drawLine(leftRuleRect.x + 1,
                leftRuleRect.y,
                leftRuleRect.x + 1,
                leftRuleRect.y + leftRuleRect.height - bottomRuleRect.height);

        g2.drawLine(bottomRuleRect.x,
                bottomRuleRect.y + 1,
                bottomRuleRect.x + bottomRuleRect.width,
                bottomRuleRect.y + 1);
    }

    private void drawHorizontalProfile(Graphics2D g2, HorizontalProfile pf, int voffset) {
        TraceFile traceFile = getFile();
        int numTraces = traceFile.numTraces();
        if (numTraces == 0) {
            return;
        }

        g2.setColor(pf.getColor());
        Point2D p1 = traceSampleToScreenCenter(0, pf.getSurfaceIndex(traceFile, 0) + voffset);

        for (int i = 1; i < numTraces; i++) {
            Point2D p2 = traceSampleToScreenCenter(i, pf.getSurfaceIndex(traceFile, i) + voffset);

            if (p2.getX() - p1.getX() > 0 || Math.abs(p2.getY() - p1.getY()) > 0) {
                g2.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
                p1 = p2;
            }
        }
    }

    private void drawFileName(Graphics2D g2, Color strokeColor) {
        Rectangle mainRect = profileField.getMainRect();
        int rectStart = -mainRect.width / 2;

        g2.setColor(strokeColor);
        g2.setFont(BOLD_FONT);

        int iconImageWidth = ResourceImageHolder.IMG_CLOSE_FILE.getWidth(null);
        TraceFile file = getFile();
        String fileName = (file.isUnsaved() ? "*" : "") + file.getFile().getName();
        g2.drawString(fileName, rectStart + 10 + iconImageWidth, 21);
    }

    private void drawLines(Graphics2D g2) {
        int selectedLineIndex;
        SelectedTrace selectedTrace = model.getSelectedTrace(this);
		TraceKey mark = selectedTrace != null ? selectedTrace.trace() : null;
        if (mark != null) {
            selectedLineIndex = getValueLineIndex(mark.getIndex());
        } else {
            selectedLineIndex = getValueLineIndex(getMiddleTrace());
        }

        Rectangle mainRect = profileField.getMainRect();
        int maxSamples = profileField.getMaxHeightInSamples();
        int lineHeight = sampleToScreen(maxSamples) - Model.TOP_MARGIN;

        NavigableMap<Integer, IndexRange> lineRanges = getFile().getLineRanges();
        for (Map.Entry<Integer, IndexRange> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            IndexRange lineRange = e.getValue();

            if (lineIndex == selectedLineIndex) {
                g2.setStroke(LEVEL_STROKE);
                g2.setColor(new Color(0, 120, 215));
                g2.setFont(BOLD_FONT);
            } else {
                g2.setStroke(AMP_STROKE);
                g2.setColor(new Color(234, 51, 35));
                g2.setFont(PLAIN_FONT);
            }

            int lineX = traceToScreen(lineRange.from());
            if (lineX > -mainRect.width / 2) {
                g2.drawLine(lineX, mainRect.y, lineX, mainRect.y + lineHeight);
            }
        }
    }

    public ProfileField getField() {
        return profileField;
    }

    public void setCursor(Cursor cursor) {
        canvas.setCursor(cursor);
    }

    private Point2D toLocalPoint(MouseEvent event) {
        return toLocalPoint(event.getSceneX(), event.getSceneY());
    }

    private Point2D toLocalPoint(double x, double y) {
        Point2D scenePoint = new Point2D(x, y);
        Point2D localPoint = canvas.sceneToLocal(scenePoint);
        return new Point2D(
                localPoint.getX() - profileField.getMainRect().x - 0.5 * getViewWidth(),
                localPoint.getY()
        );
    }

    protected EventHandler<MouseEvent> mouseClickHandler = new EventHandler<>() {
        @Override
        public void handle(MouseEvent event) {

            if (event.getClickCount() == 2) {
                // add tmp flag
                Point2D p = toLocalPoint(event);

                int traceIndex = screenToTraceSample(p).trace();
                TraceFile traceFile = profileField.getFile();
                if (traceIndex >= 0 && traceIndex < traceFile.getTraces().size()) {
                    TraceKey trace = new TraceKey(traceFile, traceIndex);
                    model.selectTrace(trace);
                    model.focusMapOnTrace(trace);
                }
            }
        }
    };

    private final EventHandler<MouseEvent> mousePressHandler =
            new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {

                    Point2D p = toLocalPoint(event);
                    if (auxEditHandler.mousePressHandle(p, GPRChart.this)) {
                        selectedMouseHandler = auxEditHandler;
                    } else if (scrollHandler.mousePressHandle(p, GPRChart.this)) {
                        selectedMouseHandler = scrollHandler;
                    } else {
                        selectedMouseHandler = null;
                    }
                    canvas.setCursor(Cursor.CLOSED_HAND);
                }
            };

    private EventHandler<MouseEvent> mouseReleaseHandler =
            new EventHandler<>() {
                @Override
                public void handle(MouseEvent event) {
                    Point2D p = toLocalPoint(event);
                    if (selectedMouseHandler != null) {
                        selectedMouseHandler.mouseReleaseHandle(p, GPRChart.this);
                        selectedMouseHandler = null;
                    }
					updateDepthRangeInMeta();
                }
            };

    protected EventHandler<MouseEvent> dragDetectedHandler =
            new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent mouseEvent) {
                    canvas.startFullDrag();
                    canvas.setCursor(Cursor.CLOSED_HAND);
                }
            };

    protected EventHandler<MouseDragEvent> dragReleaseHandler =
            new EventHandler<MouseDragEvent>() {
                @Override
                public void handle(MouseDragEvent event) {

                    Point2D p = toLocalPoint(event);

                    if (selectedMouseHandler != null) {
                        selectedMouseHandler.mouseReleaseHandle(p, GPRChart.this);
                        selectedMouseHandler = null;
                    }

                    canvas.setCursor(Cursor.DEFAULT);

                    event.consume();
                }
            };

    protected EventHandler<MouseEvent> mouseMoveHandler =
            new EventHandler<MouseEvent>() {

                @Override
                public void handle(MouseEvent event) {

                    Point2D p = toLocalPoint(event);
                    if (selectedMouseHandler != null) {
                        dragCtrlDown = event.isControlDown();
                        selectedMouseHandler.mouseMoveHandle(p, GPRChart.this);
                    } else {
                        auxEditHandler.mouseMoveHandle(p, GPRChart.this);
                    }
                }
            };


    @Override
    public double getViewWidth() {
        return profileField.getMainRect().getWidth();
    }

    @Override
    public double getViewHeight() {
        return profileField.getMainRect().getHeight();
    }

    @Override
    public int numTraces() {
        return profileField.getGprTracesCount();
    }

    @Override
    public int numSamples() {
        return profileField.getMaxHeightInSamples();
    }

    public void fitFull() {
        fit(numTraces(), numSamples());
        setStartTrace(0);
        setStartSample(0);
    }

    public void fit(int numTraces, int numSamples) {
        Rectangle viewBounds = profileField.getMainRect();

        double horizontalScale = numTraces != 0
                ? viewBounds.getWidth() / numTraces
                : 1.0;
        double verticalScale = numSamples != 0
                ? viewBounds.getHeight() / numSamples
                : 1.0;

        setHorizontalScale(horizontalScale);
        setVerticalScale(verticalScale);
    }

    private void rescaleZoom(double widthBefore, double heightBefore) {
        // update zoom scales according to changes of the viewport

        double horizontalScale = getHorizontalScale();
        if (horizontalScale == 0.0) {
            horizontalScale = 1.0;
        }
        int numTraces = (int) (widthBefore / horizontalScale);

        double verticalScale = getVerticalScale();
        if (verticalScale == 0.0) {
            verticalScale = 1.0;
        }
        int numSamples = (int) (heightBefore / verticalScale);

        fit(numTraces, numSamples);
    }

    public int getFirstVisibleTrace() {
        double x = -profileField.getMainRect().width / 2.0;
        int trace = screenToTraceSample(new Point2D(x, 0)).trace();
        return Math.clamp(trace, 0, numTraces() - 1);
    }

    public int getLastVisibleTrace() {
        double x = profileField.getMainRect().width / 2.0;
        int trace = screenToTraceSample(new Point2D(x, 0)).trace();
        return Math.clamp(trace, 0, numTraces() - 1);
    }

    public int getLastVisibleSample() {
        double y = profileField.getMainRect().height + profileField.getTopMargin();
        int sample = screenToTraceSample(new Point2D( 0, y)).sample();
        return Math.clamp(sample, 0, profileField.getMaxHeightInSamples() - 1);
    }

    public TraceSample screenToTraceSample(Point2D point) {
        double x = point.getX() + 0.5 * getViewWidth();
        double y = Math.max(0, point.getY() - profileField.getTopMargin());

        int trace = getStartTrace() + (int) (x / getHorizontalScale());
        int sample = getStartSample() + (int) (y / getVerticalScale());

        return new TraceSample(trace, sample);
    }

    public int sampleToScreen(int sample) {
        return (int) ((sample - getStartSample()) * getVerticalScale() + profileField.getTopMargin());
    }

    @Override
    public Point2D traceSampleToScreen(int trace, int sample) {
        return new Point2D(traceToScreen(trace), sampleToScreen(sample));
    }

    @Override
    public Point2D traceSampleToScreenCenter(int trace, int sample) {
        return new Point2D(
                traceToScreen(trace) + (int) (getHorizontalScale() / 2),
                sampleToScreen(sample) + (int) (getVerticalScale() / 2));
    }

    @Override
    public TraceFile getFile() {
        return profileField.getFile();
    }

    @Override
    public void selectTrace(@Nullable SelectedTrace selectedTrace, boolean focus) {
		TraceKey trace = selectedTrace != null ? selectedTrace.trace() : null;
        if (trace != null && focus) {
            scrollToTrace(trace.getIndex());
        }
        // no data stored in a chart,
        // and is taken from model on repaint
        updateScroll();
        repaint();
    }

    @Override
    public List<FoundPlace> getFlags() {
        return auxElements.stream()
                .filter(x -> x instanceof FoundPlace)
                .map(x -> (FoundPlace)x)
                .toList();
    }

    @Override
    public void selectFlag(@Nullable FoundPlace flag) {
        getFlags().forEach(x ->
                x.setSelected(Objects.equals(x, flag)));
    }

    @Override
    public void addFlag(FoundPlace flag) {
        auxElements.add(flag);
    }

    @Override
    public void removeFlag(FoundPlace flag) {
        auxElements.remove(flag);
    }

    @Override
    public void clearFlags() {
        auxElements.removeIf(x -> x instanceof FoundPlace);
    }

    // zoom

    @Override
    public int getSelectedLineIndex() {
        SelectedTrace selectedTrace = model.getSelectedTrace(this);
		TraceKey mark = selectedTrace != null ? selectedTrace.trace() : null;
        return mark != null && isTraceVisible(mark.getIndex())
                ? getValueLineIndex(mark.getIndex())
                : getValueLineIndex(getMiddleTrace());
    }

    private boolean isTraceVisible(int traceIndex) {
        return traceIndex >= getFirstVisibleTrace() && traceIndex <= getLastVisibleTrace();
    }

    private int getValueLineIndex(int index) {
        TraceFile file = profileField.getFile();
        List<GeoData> values = file.getGeoData();
        if (values == null || values.isEmpty()) {
            return 0;
        }
        // correct out of range values to point to the first or last trace
        index = Math.clamp(index, 0, values.size() - 1);
        return values.get(index).getLineOrDefault(0);
    }

    private void zoomToLine(int lineIndex) {
        TraceFile file = profileField.getFile();
        NavigableMap<Integer, IndexRange> lineRanges = file.getLineRanges();

        int firstLineIndex = !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
        lineIndex = Math.max(lineIndex, firstLineIndex);

        int lastLineIndex = !lineRanges.isEmpty() ? lineRanges.lastKey() : 0;
        lineIndex = Math.min(lineIndex, lastLineIndex);

        IndexRange range = lineRanges.get(lineIndex);

        fit(range.size(), numSamples());
        setStartTrace(range.from());
        setStartSample(0);

        model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
    }

    @Override
    public void zoomToCurrentLine() {
        // if there's a selection mark present on the chart
        // zoom to a line containing that mark;
        // otherwise zoom to a line containing
        // the middle trace
        int lineIndex = getSelectedLineIndex();
        zoomToLine(lineIndex);
    }

    @Override
    public void zoomToPreviousLine() {
        int lineIndex = getValueLineIndex(getMiddleTrace());
        zoomToLine(lineIndex - 1);
    }

    @Override
    public void zoomToNextLine() {
        int lineIndex = getValueLineIndex(getMiddleTrace());
        zoomToLine(lineIndex + 1);
    }

    @Override
    public void zoomToFit() {
        fitFull();
        model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
    }

    @Override
    public void zoomIn() {
        zoom(ZOOM_IN);
    }

    @Override
    public void zoomOut() {
        zoom(ZOOM_OUT);
    }

	public boolean isDragCtrlDown() {
		return dragCtrlDown;
	}
}

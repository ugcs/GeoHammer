package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.github.thecoldwine.sigrun.common.ext.TraceKey;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.CloseGprChartButton;
import com.ugcs.gprvisualizer.app.auxcontrol.DepthHeight;
import com.ugcs.gprvisualizer.app.auxcontrol.DepthStart;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.RemoveLineButton;
import com.ugcs.gprvisualizer.app.events.FileClosedEvent;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.service.TemplateUnitService;
import com.ugcs.gprvisualizer.draw.PrismDrawer;
import com.ugcs.gprvisualizer.draw.ShapeHolder;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.HorizontalRulerController;
import com.ugcs.gprvisualizer.gpr.LeftRulerController;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.ui.BaseSlider;
import com.ugcs.gprvisualizer.utils.Range;
import javafx.application.Platform;
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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;

public class GPRChart extends Chart {

    private static final Color BACK_GROUD_COLOR = new Color(244, 244, 244);
    private static final Stroke AMP_STROKE = new BasicStroke(1.0f);
    private static final Stroke LEVEL_STROKE = new BasicStroke(1.0f);

    private static final double ASPECT_A = 1.14;

    private static final Font fontB = new Font("Verdana", Font.BOLD, 8);
    private static final Font fontP = new Font("Verdana", Font.PLAIN, 8);

    private static final float[] dash1 = {5.0f};
    private static final BasicStroke dashed =
            new BasicStroke(1.0f,
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    10.0f, dash1, 0.0f);

    private BaseObject selectedMouseHandler;
    private final BaseObject scrollHandler;

    private final Model model;
    private final AuxElementEditHandler auxEditHandler;

    private int width = 800;
    private int height = 600;

    private double contrast = 50;

    private final VBox vbox = new VBox();
    private final Canvas canvas = new Canvas(width, height);
    private final FXGraphics2D g2 = new FXGraphics2D(canvas.getGraphicsContext2D());

    private final PrismDrawer prismDrawer;
    private final ContrastSlider contrastSlider;

    private final MutableInt shiftGround = new MutableInt(0);

    private final LeftRulerController leftRulerController;

    private final HorizontalRulerController horizontalRulerController;

    private final ChangeListener<Number> sliderListener
            = (observable, oldValue, newValue) -> {
                //if (Math.abs(newValue.intValue() - oldValue.intValue()) > 3) {
                    repaintEvent();
                //}
            };

    private final ProfileField profileField;
    private final List<BaseObject> auxElements = new ArrayList<>();

    VerticalRulerDrawer verticalRulerDrawer = new VerticalRulerDrawer(this);
    HorizontalRulerDrawer horizontalRulerDrawer = new HorizontalRulerDrawer(this);

    public GPRChart(Model model, TraceFile traceFile, TemplateUnitService templateUnitService) {
        super(model);
        this.model = model;
        this.auxEditHandler = model.getAuxEditHandler();
        this.profileField = new ProfileField(traceFile);
        this.leftRulerController = new LeftRulerController(profileField);
        this.horizontalRulerController = new HorizontalRulerController(model, traceFile, templateUnitService);

        vbox.getChildren().addAll(canvas);
        vbox.setOnMouseClicked(event -> {
            select();
        });
        prismDrawer = new PrismDrawer(model);
        initCanvas();

        contrastSlider = new ContrastSlider(profileField.getProfileSettings(), sliderListener);

        getProfileScroll().setChangeListener(new ChangeListener<Number>() {
            //TODO: fix with change listener
            Number currentValue;

            public void changed(ObservableValue<? extends Number> ov,
                                Number oldVal, Number newVal) {
                //if (currentValue == null) { currentValue = newVal;}
                //if (currentValue != null && newVal != null && Math.abs(newVal.intValue() - currentValue.intValue()) > 3) {
                //    currentValue = newVal;
                    repaintEvent();
                //}
            }
        });

        scrollHandler = new CleverViewScrollHandler(this);
        updateAuxElements();
    }

    public void close() {
        if (!confirmUnsavedChanges()) {
            return;
        }
        model.publishEvent(new FileClosedEvent(this, getFile()));
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

        getField().setViewDimension(new Dimension(this.width, this.height));
        rescaleZoom(viewWidthBefore, viewHeightBefore);
        repaintEvent();
    }

    public List<BaseObject> getAuxElements() {
        return auxElements;
    }

    private class ContrastSlider extends BaseSlider {
        public ContrastSlider(Settings settings, ChangeListener<Number> listenerExt) {
            super(settings, listenerExt);
            name = "Contrast";
            units = "";
            tickUnits = 25;
        }

        public void updateUI() {
            slider.setMax(100);
            slider.setMin(0);
            slider.setValue(contrast);
        }

        public int updateModel() {
            contrast = (int) slider.getValue();
            return (int) contrast;
        }
    }

    private void initCanvas() {
        canvas.setOnScroll(event -> {
            int ch = (event.getDeltaY() > 0 ? 1 : -1);

            double ex = event.getSceneX();
            double ey = event.getSceneY();

            zoom(ch, ex, ey, event.isControlDown());

            event.consume(); // don't scroll the page
        });

        canvas.setOnMousePressed(mousePressHandler);
        canvas.setOnMouseReleased(mouseReleaseHandler);
        canvas.setOnMouseMoved(mouseMoveHandler);
        canvas.setOnMouseClicked(mouseClickHandler);
        canvas.addEventFilter(MouseEvent.DRAG_DETECTED, dragDetectedHandler);
        canvas.addEventFilter(MouseEvent.MOUSE_DRAGGED, mouseMoveHandler);
        canvas.addEventFilter(MouseDragEvent.MOUSE_DRAG_RELEASED, dragReleaseHandler);
    }

    void zoom(int ch, boolean justHorizont) {
        var ex = width / 2;
        var ey = height / 2;
        zoom(ch, ex, ey, justHorizont);
    }

    private void zoom(int ch, double ex, double ey, boolean justHorizont) {

        Point2D t = getLocalCoords(ex, ey);

        TraceSample ts = screenToTraceSample(t);

        if (justHorizont) {

            double realAspect = getRealAspect()
                    * (ch > 0 ? ASPECT_A :
                    1 / ASPECT_A);

            setRealAspect(realAspect);
        } else {
            double zoom = clampZoom(getZoom() + ch);
            setZoom(zoom);
        }

        ////

        Point2D t2 = getLocalCoords(ex, ey);
        TraceSample ts2 = screenToTraceSample(t2);

        setMiddleTrace(getMiddleTrace()
                - (ts2.getTrace() - ts.getTrace()));

        int starts = getStartSample() - (ts2.getSample() - ts.getSample());
        setStartSample(starts);

        updateScroll();
        repaintEvent();
    }

    private double clampZoom(double zoom) {
        Rectangle mainRect = profileField.getMainRect();
        double viewHeight = mainRect.getHeight();

        int numSamples = profileField.getMaxHeightInSamples();
        double vScale = numSamples != 0
                ? viewHeight / numSamples
                : 0.0;

        double minZoom = Math.log(vScale) / Math.log(ZOOM_A);
        return Math.max(minZoom, zoom);
    }

    @Override
    public void setMiddleTrace(int selectedTrace) {
        int lineIndexBefore = getSelectedLineIndex();

        super.setMiddleTrace(selectedTrace);

        int lineIndexAfter = getSelectedLineIndex();
        if (lineIndexBefore != lineIndexAfter) {
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
        }
    }

    void updateScroll() {
        if (!model.isActive() || getField().getGprTracesCount() == 0) {
            return;
        }
        getProfileScroll().recalc();
    }

    private void select() {
        model.selectChart(this);
    }

    @Override
    public Node getRootNode() {
        return vbox;
    }

    @Override
    public void selectFile() {
        model.publishEvent(new FileSelectedEvent(this, profileField.getFile()));
    }

    private void draw(int width, int height) {
        if (width <= 0 || height <= 0 || !model.isActive() || profileField.getGprTracesCount() == 0) {
            return;
        }

        if (!(canvas.getWidth() == width && canvas.getHeight() == height)) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            // fitFull();
        }

        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] buffer = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = BACK_GROUD_COLOR.getRGB();
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g2.setClip(null);
        g2.setColor(BACK_GROUD_COLOR);
        g2.fillRect(0, 0, (int) canvas.getWidth(), (int) canvas.getHeight());

        drawAxis(g2);

        verticalRulerDrawer.draw(g2);
        horizontalRulerDrawer.draw(g2);

        var mainRect = profileField.getMainRect();
        g2.setClip(mainRect.x, mainRect.y, mainRect.width, mainRect.height);
        prismDrawer.draw(width, this, g2, buffer, getRealContrast());
        g2.drawImage(bi, 0, 0, (int) width, (int) height, null);

        g2.translate(mainRect.x + mainRect.width / 2, 0);

        drawAuxGraphics1(g2);
        drawAuxElements(g2);

        var clipTopMainRect = profileField.getClipTopMainRect();
        g2.setClip(clipTopMainRect.x,
                clipTopMainRect.y,
                clipTopMainRect.width,
                clipTopMainRect.height);

        drawFileName(g2);
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
        g2.setStroke(dashed);

        var profileSettings = profileField.getProfileSettings();

        int y = (int) traceSampleToScreen(new TraceSample(0, profileSettings.getLayer())).getY();
        g2.drawLine((int) -width / 2, y, (int) width / 2, y);

        int bottomSelectedSmp = profileSettings.getLayer() + profileSettings.hpage;
        int y2 = (int) traceSampleToScreen(new TraceSample(
                0, bottomSelectedSmp)).getY();

        g2.drawLine((int) -width / 2, y2, (int) width / 2, y2);
    }

    private void drawAuxElements(Graphics2D g2) {

        boolean full = true; //!controller.isEnquiued();

        //for (BaseObject bo : model.getAuxElements()) {
        for (BaseObject bo : auxElements) {
            if (full || bo.isSelected()) {
                bo.drawOnCut(g2, this);
            }
        }

        for (TraceKey mark : model.getSelectedTraces()) {
            if (!Objects.equals(mark.getFile(), getFile())) {
                continue;
            }
            ClickPlace clickPlace = new ClickPlace(mark);
            clickPlace.drawOnCut(g2, this);
        }
    }

    public void updateAuxElements() {
        auxElements.clear();

        TraceFile file = profileField.getFile();
        auxElements.addAll(file.getAuxElements());

        // line removal buttons
        SortedMap<Integer, Range> lineRanges = getFile().getLineRanges();
        for (Range range : lineRanges.values()) {
            RemoveLineButton removeLine = new RemoveLineButton(
                    new TraceKey(file, range.getMin().intValue()),
                    model);
            auxElements.add(removeLine);
        }

        auxElements.add(new DepthStart(ShapeHolder.topSelection));
        auxElements.add(new DepthHeight(ShapeHolder.botSelection));
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

    private void drawAxis(Graphics2D g2) {
        var field = getField();

        Rectangle mainRectRect = field.getMainRect();
        Rectangle topRuleRect = field.getTopRuleRect();
        Rectangle leftRuleRect = field.getLeftRuleRect();
        Rectangle bottomRuleRect = field.getBottomRuleRect();

        g2.setPaint(Color.lightGray);
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(topRuleRect.x,
                topRuleRect.y + topRuleRect.height + 1,
                topRuleRect.x + topRuleRect.width,
                topRuleRect.y + topRuleRect.height + 1);

        g2.drawLine(topRuleRect.x,
                topRuleRect.y + topRuleRect.height + 1,
                topRuleRect.x,
                mainRectRect.height);

        g2.drawLine(leftRuleRect.x + 1,
                leftRuleRect.y,
                leftRuleRect.x + 1,
                leftRuleRect.y + leftRuleRect.height);

        g2.drawLine(bottomRuleRect.x,
                bottomRuleRect.y + 1,
                bottomRuleRect.x + bottomRuleRect.width,
                bottomRuleRect.y + 1);
    }

    private void drawHorizontalProfile(Graphics2D g2,
            HorizontalProfile pf, int voffset) {
        g2.setColor(pf.getColor());
        Point2D p1 = traceSampleToScreenCenter(new TraceSample(
                0, pf.getDepth(0) + voffset));
        int max2 = 0;

        for (int i = 1; i < pf.size(); i++) {

            max2 = Math.max(max2, pf.getDepth(i) + voffset);

            Point2D p2 = traceSampleToScreenCenter(new TraceSample(i, max2));

            if (p2.getX() - p1.getX() > 0 || Math.abs(p2.getY() - p1.getY()) > 0) {
                g2.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
                p1 = p2;
                max2 = 0;
            }
        }
    }

    private void drawFileName(Graphics2D g2) {
        Rectangle mainRect = profileField.getMainRect();
        int rectStart = -mainRect.width / 2;

        g2.setColor(Color.darkGray);
        g2.setFont(fontB);

        int iconImageWidth = ResourceImageHolder.IMG_CLOSE_FILE.getWidth(null);
        TraceFile file = getFile();
        String fileName = (file.isUnsaved() ? "*" : "") + file.getFile().getName();
        g2.drawString(fileName, rectStart + 10 + iconImageWidth, 21);
    }

    private void drawLines(Graphics2D g2) {
        int selectedLineIndex;
        TraceKey mark = model.getSelectedTrace(this);
        if (mark != null) {
            selectedLineIndex = getValueLineIndex(mark.getIndex());
        } else {
            selectedLineIndex = getValueLineIndex(getMiddleTrace());
        }

        Rectangle mainRect = profileField.getMainRect();
        int maxSamples = profileField.getMaxHeightInSamples();
        int lineHeight = sampleToScreen(maxSamples) - Model.TOP_MARGIN;

        SortedMap<Integer, Range> lineRanges = getFile().getLineRanges();
        for (Map.Entry<Integer, Range> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            Range lineRange = e.getValue();

            if (lineIndex == selectedLineIndex) {
                g2.setStroke(LEVEL_STROKE);
                g2.setColor(new Color(0, 120, 215));
                g2.setFont(fontB);
            } else {
                g2.setStroke(AMP_STROKE);
                g2.setColor(new Color(234, 51, 35));
                g2.setFont(fontP);
            }

            Point2D lineStart = traceSampleToScreen(
                    new TraceSample(lineRange.getMin().intValue(), 0));
            if (lineStart.getX() > (double)-mainRect.width / 2) {
                g2.drawLine((int)lineStart.getX(), mainRect.y,
                        (int)lineStart.getX(), mainRect.y + lineHeight);
            }
        }
    }

    public ProfileField getField() {
        return profileField;
    }

    public void setCursor(Cursor cursor) {
        canvas.setCursor(cursor);
    }

    public void repaint() {
        draw(width, height);
    }

    void repaintEvent() {
        if (!model.isLoading() && getField().getGprTracesCount() > 0) {
            //controller.render();
            Platform.runLater(this::repaint);
        }
    }

    private Point2D getLocalCoords(MouseEvent event) {
        return getLocalCoords(event.getSceneX(), event.getSceneY());
    }

    private Point2D getLocalCoords(double x, double y) {
        Point2D sceneCoords = new Point2D(x, y);
        Point2D imgCoord = canvas.sceneToLocal(sceneCoords);
        Point2D p = new Point2D(imgCoord.getX() - getField().getMainRect().x
                - getField().getMainRect().width / 2,
                imgCoord.getY());
        return p;
    }

    protected EventHandler<MouseEvent> mouseClickHandler = new EventHandler<>() {
        @Override
        public void handle(MouseEvent event) {

            if (event.getClickCount() == 2) {
                // add tmp flag
                Point2D p = getLocalCoords(event);

                int traceIndex = screenToTraceSample(p).getTrace();
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

                    Point2D p = getLocalCoords(event);
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
                    Point2D p = getLocalCoords(event);
                    if (selectedMouseHandler != null) {
                        selectedMouseHandler.mouseReleaseHandle(p, GPRChart.this);
                        selectedMouseHandler = null;
                    }
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

                    Point2D p = getLocalCoords(event);

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

                    Point2D p = getLocalCoords(event);
                    if (selectedMouseHandler != null) {
                        selectedMouseHandler.mouseMoveHandle(p, GPRChart.this);
                    } else {
                        if (!auxEditHandler.mouseMoveHandle(p, GPRChart.this)) {
                            //do nothing
                        }
                    }
                }
            };

    @Override
    public int getTracesCount() {
        return getField().getGprTracesCount();
    }

    @Override
    public void clear() {
        super.clear();
        //aspect = -15;
        //startSample = 0;
        if (model.isActive() && getField().getGprTracesCount() > 0) {
            fitFull();
        }
    }

    public void fitFull() {
        int middle = getField().getGprTracesCount() / 2;

        setMiddleTrace(middle);
        fit(getField().getMaxHeightInSamples(), 2 * middle);
    }

    public void fit(int numSamples, int numTraces) {
        Rectangle mainRect = profileField.getMainRect();
        double viewWidth = mainRect.getWidth();
        double viewHeight = mainRect.getHeight();

        double vScale = numSamples != 0
                ? viewHeight / numSamples
                : 0.0;
        double zoom = Math.log(vScale) / Math.log(ZOOM_A);
        setZoom(zoom);
        setStartSample(0);

        double hScale = numTraces != 0
                ? viewWidth / numTraces
                : 1.0;

        double realAspect = hScale / getVScale();
        setRealAspect(realAspect);
    }

    private void rescaleZoom(double widthBefore, double heightBefore) {
        // update zoom scales according to changes of the viewport

        double vScale = getVScale();
        if (vScale == 0.0) {
            vScale = 1.0;
        }
        int numSamples = (int) (heightBefore / vScale);

        double hScale = getHScale();
        if (hScale == 0.0) {
            hScale = 1.0;
        }
        int numTraces = (int) (widthBefore / hScale);

        fit(numSamples, numTraces);
    }

    public int getFirstVisibleTrace() {
        double x = -getField().getMainRect().width / 2.0;
        int trace = screenToTraceSample(new Point2D(x, 0)).getTrace();
        return Math.clamp(trace, 0, getTracesCount() - 1);
    }

    public int getLastVisibleTrace() {
        double x = profileField.getMainRect().width / 2.0;
        int trace = screenToTraceSample(new Point2D(x, 0)).getTrace();
        return Math.clamp(trace, 0, getTracesCount() - 1);
    }

    public int getLastVisibleSample() {
        double y = profileField.getMainRect().height + profileField.getTopMargin();
        int sample = screenToTraceSample(new Point2D( 0, y)).getSample();
        return Math.clamp(sample, 0, profileField.getMaxHeightInSamples() - 1);
    }

    public void setStartSample(int startSample) {
        if(getScreenImageSize().height < getField().getViewDimension().height){
            startSample = 0;
        }
        this.startSample = Math.max(0, startSample);
    }

    public Dimension getScreenImageSize() {
        return new Dimension(
                (int) (getField().getGprTracesCount() * getHScale()),
                (int) (getField().getMaxHeightInSamples() * getVScale()));
    }

    @Override
    public int getVisibleNumberOfTrace() {
        Point2D p = traceSampleToScreen(new TraceSample(0,0));
        Point2D p2 = new Point2D(p.getX() + getField().getMainRect().width, 0);
        TraceSample t2 = screenToTraceSample(p2);

        return t2.getTrace();
    }

    public TraceSample screenToTraceSample(Point2D point) {
        int trace = getMiddleTrace() + (int) (point.getX() / getHScale());
        int sample = getStartSample() + (int) ((point.getY() - getField().getTopMargin()) / getVScale());

        return new TraceSample(trace, sample);
    }

    public int sampleToScreen(int sample) {
        return (int) ((sample - getStartSample()) * getVScale() + getField().getTopMargin());
    }

    public Point2D traceSampleToScreen(TraceSample ts) {
        return new Point2D(traceToScreen(ts.getTrace()), sampleToScreen(ts.getSample()));
    }

    public Point2D traceSampleToScreenCenter(TraceSample ts) {
        return new Point2D(
                traceToScreen(ts.getTrace()) + (int) (getHScale() / 2),
                sampleToScreen(ts.getSample()) + (int) (getVScale() / 2));
    }

    @Override
    public TraceFile getFile() {
        return profileField.getFile();
    }

    @Override
    public void selectTrace(TraceKey trace, boolean focus) {
        if (trace != null && focus) {
            setMiddleTrace(trace.getIndex());
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
    public void selectFlag(FoundPlace flag) {
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
        TraceKey mark = model.getSelectedTrace(this);
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
        return values.get(index).getLineIndexOrDefault();
    }

    private void zoomToLine(int lineIndex) {
        TraceFile file = profileField.getFile();
        SortedMap<Integer, Range> lineRanges = file.getLineRanges();

        int firstLineIndex = !lineRanges.isEmpty() ? lineRanges.firstKey() : 0;
        lineIndex = Math.max(lineIndex, firstLineIndex);

        int lastLineIndex = !lineRanges.isEmpty() ? lineRanges.lastKey() : 0;
        lineIndex = Math.min(lineIndex, lastLineIndex);

        Range range = lineRanges.get(lineIndex);

        int maxSamples = profileField.getMaxHeightInSamples();
        int numTraces = range.getMax().intValue() - range.getMin().intValue() + 1;
        // as middle trace is an exact half of the viewport
        // num traces should be even to properly calculate horizontal scale factor
        numTraces &= ~1;

        setMiddleTrace(range.getMin().intValue() + numTraces / 2);
        fit(maxSamples, numTraces);

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
        zoom(1, false);
    }

    @Override
    public void zoomOut() {
        zoom(-1, false);
    }
}

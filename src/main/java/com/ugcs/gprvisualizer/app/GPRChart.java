package com.ugcs.gprvisualizer.app;

import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.CloseAllFilesButton;
import com.ugcs.gprvisualizer.app.auxcontrol.DepthHeight;
import com.ugcs.gprvisualizer.app.auxcontrol.DepthStart;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.auxcontrol.RemoveFileButton;
import com.ugcs.gprvisualizer.draw.PrismDrawer;
import com.ugcs.gprvisualizer.draw.ShapeHolder;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.LeftRulerController;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.ui.BaseSlider;
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
import org.jspecify.annotations.NonNull;
import org.jfree.fx.FXGraphics2D;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    private final ChangeListener<Number> sliderListener
            = (observable, oldValue, newValue) -> {
                //if (Math.abs(newValue.intValue() - oldValue.intValue()) > 3) {
                    repaintEvent();
                //}
            };

    private final ProfileField profileField;
    private final List<BaseObject> auxElements = new ArrayList<>();

    public GPRChart(Model model, AuxElementEditHandler auxEditHandler, List<SgyFile> sgyFiles) {
        super(model);
        this.model = model;
        this.auxEditHandler = auxEditHandler;
        this.profileField = new ProfileField(sgyFiles);
        this.leftRulerController = new LeftRulerController(profileField);

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

    public BaseSlider getContrastSlider() {
        return contrastSlider;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;

        getField().setViewDimension(new Dimension((int) this.width, (int) this.height));
        System.out.println("setSize: " + width + "x" + height);
        repaintEvent();
    }

    public List<BaseObject> getAuxElements() {
        return auxElements;
    }

    public void addSgyFile(@NonNull SgyFile f) {
        profileField.addSgyFile(f);
        updateAuxElements();
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
            setZoom(getZoom() + ch);
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

    VerticalRulerDrawer verticalRulerDrawer = new VerticalRulerDrawer(this);

    @Override
    public void selectFile() {
        model.publishEvent(new FileSelectedEvent(this, profileField.getSgyFileByTrace(getMiddleTrace())));
    }

    private void draw(int width, int height) {
        if (width <= 0 || height <= 0 || !model.isActive() || getField().getGprTracesCount() == 0) {
            return;
        }

        if (!(canvas.getWidth() == width && canvas.getHeight() == height)) {
            canvas.setWidth(width);
            canvas.setHeight(height);
            // fitFull();
            System.out.println("change sizes: " + width + "x" + height);
        }

        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] buffer = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = BACK_GROUD_COLOR.getRGB();
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Очистка канвы
        g2.setColor(BACK_GROUD_COLOR);
        g2.fillRect(0, 0, (int) canvas.getWidth(), (int) canvas.getHeight());

        drawAxis(g2);
        //new VerticalRulerDrawer(this).draw(g2);

        verticalRulerDrawer.draw(g2);

        var mainRect = profileField.getMainRect();
        g2.setClip(mainRect.x, mainRect.y, mainRect.width, mainRect.height);
        prismDrawer.draw(width, this, g2, buffer, getRealContrast());
        g2.drawImage(bi, 0, 0, (int) width, (int) height, null);

        g2.translate(mainRect.x + mainRect.width / 2, 0);

        //if (!controller.isEnquiued()) {
            // skip if another recalculation coming
            drawAuxGraphics1(g2);
        //}

        drawAuxElements(g2);

        //if (!controller.isEnquiued()) {
            // skip if another recalculation coming

        var clipTopMainRect = profileField.getClipTopMainRect();
            g2.setClip(clipTopMainRect.x,
                    clipTopMainRect.y,
                    clipTopMainRect.width,
                    clipTopMainRect.height);

            drawFileNames(height - 30, g2);
        //}

        g2.translate(-(mainRect.x + mainRect.width / 2), 0);
    }

    private void drawAuxGraphics1(Graphics2D g2) {
        Rectangle r = profileField.getClipMainRect();
        g2.setClip(r.x, r.y, r.width, r.height);

        drawFileProfiles(g2);
        drawAmplitudeMapLevels(g2);
    }

    private void drawFileProfiles(Graphics2D graphicsContext) {

        int startTrace = getFirstVisibleTrace();
        int finishTrace = getLastVisibleTrace();

        List<SgyFile> visibleFiles = profileField.getFilesInRange(startTrace, finishTrace);

        for (SgyFile currentFile : visibleFiles) {

            if (currentFile.profiles != null) {
                // pf
                graphicsContext.setColor(new Color(50, 200, 250));
                graphicsContext.setStroke(AMP_STROKE);
                for (HorizontalProfile pf : currentFile.profiles) {
                    drawHorizontalProfile(graphicsContext,
                            currentFile.getOffset().getStartTrace(), pf, 0);
                }
            }

            // ground
            if (currentFile.getGroundProfile() != null) {
                graphicsContext.setColor(new Color(210, 105, 30));
                graphicsContext.setStroke(LEVEL_STROKE);
                drawHorizontalProfile(graphicsContext,
                        currentFile.getOffset().getStartTrace(), currentFile.getGroundProfile(),
                        shiftGround.intValue());
            }
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

        for (ClickPlace clickPlace : model.getSelectedTraces()) {
            Trace trace = clickPlace.getTrace();
            if (trace != null && equals(model.getFileChart(trace.getFile()))) {
                clickPlace.drawOnCut(g2, this);
            }
        }
    }


        public void updateAuxElements() {
            auxElements.clear();
            for (SgyFile sf : profileField.getSgyFiles()) {
                auxElements.addAll(sf.getAuxElements());

                Trace lastTrace = sf.getTraces().get(sf.getTraces().size() - 1);

                // add remove button
                RemoveFileButton rfb = new RemoveFileButton(1,
                        //lastTrace.getIndexInFile(),
                        sf.getOffset(), sf, model);
                auxElements.add(rfb);

            }
            auxElements.add(new DepthStart(ShapeHolder.topSelection));
            auxElements.add(new DepthHeight(ShapeHolder.botSelection));
            auxElements.add(leftRulerController.getTB());
            if (profileField.getSgyFiles().size() > 1) {
                auxElements.add(new CloseAllFilesButton(model));
            }
        }

    private final LeftRulerController leftRulerController;

    public LeftRulerController getLeftRulerController() {
        return leftRulerController;
    }

    private void drawAxis(Graphics2D g2) {

        var field = getField();

        Rectangle mainRectRect = field.getMainRect();
        Rectangle topRuleRect = field.getTopRuleRect();
        Rectangle leftRuleRect = field.getLeftRuleRect();

        g2.setPaint(Color.lightGray);
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(topRuleRect.x,
                topRuleRect.y + topRuleRect.height + 1,
                topRuleRect.x + topRuleRect.width,
                topRuleRect.y + topRuleRect.height + 1);

        g2.setPaint(Color.lightGray);
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(topRuleRect.x,
                topRuleRect.y + topRuleRect.height + 1,
                topRuleRect.x,
                mainRectRect.height);

        g2.setPaint(Color.lightGray);
        g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(leftRuleRect.x + 1,
                leftRuleRect.y,
                leftRuleRect.x + 1,
                mainRectRect.height);
    }

    private void drawHorizontalProfile(Graphics2D g2,
                                       int startTraceIndex, HorizontalProfile pf,
                                       int voffset) {

        g2.setColor(pf.color);
        Point2D p1 = traceSampleToScreenCenter(new TraceSample(
                startTraceIndex, pf.deep[0] + voffset));
        int max2 = 0;

        for (int i = 1; i < pf.deep.length; i++) {

            max2 = Math.max(max2, pf.deep[i] + voffset);

            Point2D p2 = traceSampleToScreenCenter(new TraceSample(
                    startTraceIndex + i, max2));

            if (p2.getX() - p1.getX() > 0 || Math.abs(p2.getY() - p1.getY()) > 0) {
                g2.drawLine((int) p1.getX(), (int) p1.getY(), (int) p2.getX(), (int) p2.getY());
                p1 = p2;
                max2 = 0;
            }
        }
    }

    private void drawFileNames(int height, Graphics2D g2) {
        SgyFile currentFile = null;
        ClickPlace mark = model.getSelectedTrace(this);
        if (mark != null) {
            currentFile = mark.getTrace().getFile();
        }
        if (currentFile == null) {
            currentFile = profileField.getSgyFileByTrace(getMiddleTrace());
        }

        int selectedX1 = 0;
        int selectedX2 = 0;
        Point2D p = null;
        Point2D p2 = null;

        int leftMargin = -getField().getMainRect().width / 2;

        g2.setStroke(AMP_STROKE);
        for (SgyFile fl : profileField.getSgyFiles()) {

            p = traceSampleToScreen(new TraceSample(
                    fl.getTraces().get(0).getIndexInSet(), 0));

            int lastTraceIndex = fl.getTraces().size() - 1;
            p2 = traceSampleToScreen(new TraceSample(
                    fl.getTraces().get(lastTraceIndex).getIndexInSet(), 0));

            if (currentFile == fl) {
                g2.setColor(new Color(0, 120, 215));
                g2.setFont(fontB);

                selectedX1 = (int) p.getX();
                selectedX2 = (int) p2.getX();
            } else {
                g2.setColor(Color.darkGray);
                g2.setFont(fontP);
            }
            /// separator
            if (p.getX() > (double) -getField().getMainRect().width / 2) {
                g2.drawLine((int) p.getX(), 0, (int) p.getX(), height);
            }

            p = new Point2D(Math.max(p.getX(), leftMargin), p.getY());

            int iconImageWidth = ResourceImageHolder.IMG_CLOSE_FILE.getWidth(null);
            g2.setClip((int) p.getX(), 0, (int) (p2.getX() - p.getX()), 20);
            String fileName = (fl.isUnsaved() ? "*" : "") + fl.getFile().getName();
            g2.drawString(fileName, (int) p.getX() + 4 + iconImageWidth, 11);
            g2.setClip(null);
        }

        if (p2 != null) {
            g2.drawLine((int) p2.getX(), 0, (int) p2.getX(), height);
        }

        if (currentFile != null) {
            g2.setColor(new Color(0, 120, 215));
            g2.setStroke(LEVEL_STROKE);
            if (selectedX1 >= leftMargin) {
                g2.drawLine(selectedX1, 0, selectedX1, height);
            }
            g2.drawLine(selectedX2, 0, selectedX2, height);
        }
    }

    public ProfileField getField() {
        return profileField;
    }

    public void setCursor(Cursor cursor) {
        canvas.setCursor(cursor);
    }

    private void repaint() {
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

    protected EventHandler<MouseEvent> mouseClickHandler = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent event) {

            if (event.getClickCount() == 2) {
                // add tmp flag
                Point2D p = getLocalCoords(event);

                int traceIndex = screenToTraceSample(p).getTrace();
                if (traceIndex >= 0 && traceIndex < getField().getGprTracesCount()) {
                    Trace trace = getField().getGprTraces().get(traceIndex);
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
        setMiddleTrace(getField().getGprTracesCount() / 2);
        fit(getField().getMaxHeightInSamples() * 2, getField().getGprTracesCount());
    }

    public void fit(int maxSamples, int tracesCount) {
        double vertScale = 1.0;
        if (getField().getViewDimension().height != 0) {
            vertScale = (double) getField().getViewDimension().height
                    / (double) maxSamples;
        }
        double zoom = Math.log(vertScale) / Math.log(ZOOM_A);

        setZoom((int) zoom);
        setStartSample(0);

        double h = 0.1;
        if (getField().getViewDimension().width != 0) {
            h = (double) (getField().getViewDimension().width
                    - getField().getLeftRuleRect().width - 30)
                    / ((double) tracesCount);
        }

        double realAspect = h / getVScale();
        setRealAspect(realAspect);
    }

    public int getFirstVisibleTrace() {
        return Math.clamp(screenToTraceSample(new Point2D(- getField().getMainRect().width / 2, 0)).getTrace(),
                0, getTracesCount() - 1);
    }

    public int getLastVisibleTrace() {
        return Math.clamp(screenToTraceSample(new Point2D(getField().getMainRect().width / 2, 0)).getTrace(),
                0, getTracesCount() - 1);
    }

    public int getLastVisibleSample(int height) {
        return screenToTraceSample(new Point2D( 0, height)).getSample();
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

    public TraceSample screenToTraceSample(Point2D point, VerticalCutPart vcp) {
        int trace = vcp.globalToLocal(getMiddleTrace()
                + (int)((point.getX()) / getHScale()));

        int sample = getStartSample()
                + (int) ((point.getY() - getField().getTopMargin()) / getVScale());

        return new TraceSample(trace, sample);
    }

    public TraceSample screenToTraceSample(Point2D point) {
        int trace = getMiddleTrace() + (int) (-1 + (point.getX()) / getHScale());
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
    public List<SgyFile> getFiles() {
        return Collections.unmodifiableList(profileField.getSgyFiles());
    }

    @Override
    public void selectTrace(Trace trace, boolean focus) {
        if (trace != null && focus) {
            setMiddleTrace(trace.getIndexInSet());
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
}

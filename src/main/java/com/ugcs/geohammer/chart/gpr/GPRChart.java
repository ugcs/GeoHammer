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
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.TraceFile;
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
import com.ugcs.geohammer.model.Range;
import javafx.animation.AnimationTimer;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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
	private static final Logger log = LoggerFactory.getLogger(GPRChart.class);

	private static final int MIN_CONTRAST = 0;
	private static final int MAX_CONTRAST = 100;

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
					setContrastToMeta();
		//}
            };

    private final ProfileField profileField;
    private final List<BaseObject> auxElements = new ArrayList<>();

	private final AtomicBoolean needsRepaint = new AtomicBoolean(false);

	private final AnimationTimer repaintTimer;

	private BufferedImage cachedBufferedImage;

	private int[] cachedBuffer;

	private int cachedWidth = -1;

	private int cachedHeight = -1;

    VerticalRulerDrawer verticalRulerDrawer = new VerticalRulerDrawer(this);
    HorizontalRulerDrawer horizontalRulerDrawer = new HorizontalRulerDrawer(this);

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

        contrastSlider = new ContrastSlider(profileField.getProfileSettings(), sliderListener);

		setContrastFromMeta(contrastSlider, traceFile);
		setAmplitudeRangeFromMeta(traceFile);

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

		repaintTimer = new AnimationTimer() {
			@Override
			public void handle(long now) {
				if (needsRepaint.get()) {
					needsRepaint.set(false);
					repaint();
				}
			}
		};
		repaintTimer.start();

		scrollHandler = new CleverViewScrollHandler(this);
        updateAuxElements();
    }

    public void close() {
		if (repaintTimer != null) {
			repaintTimer.stop();
		}

		if (!confirmUnsavedChanges()) {
			return;
		}
		model.publishEvent(new FileClosedEvent(this, getFile()));
    }

    @Override
    public void init() {
        vbox.setPrefHeight(Math.max(Chart.MIN_HEIGHT, vbox.getScene().getHeight()));
        vbox.setMinHeight(Math.max(Chart.MIN_HEIGHT, vbox.getScene().getHeight() / 2));

        ProfileView profileView = AppContext.getInstance(ProfileView.class);
        setSize(
                (int)(profileView.getCenter().getWidth() - 21),
                (int)(Math.max(400, vbox.getHeight()) - 4));
        fitFull();
    }

    @Override
    public void repaint() {
        draw(width, height);
    }

	public void repaintEvent() {
		if (!model.isLoading() && getField().getGprTracesCount() > 0) {
			needsRepaint.set(true);
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

        getField().setViewDimension(new Dimension(this.width, this.height));
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

	private void setAmplitudeRangeToMeta() {
		TraceFile traceFile = profileField.getFile();
		MetaFile meta = traceFile.getMetaFile();
		if (meta != null) {
			var profileSettings = profileField.getProfileSettings();
			int min = profileSettings.getLayer();
			int max = min + profileSettings.hpage;
			meta.setAmplitudeRange(new Range(min, max));
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

	private void setAmplitudeRangeFromMeta(TraceFile traceFile) {
		MetaFile meta = traceFile.getMetaFile();
		Range savedRange = meta != null ? meta.getAmplitudeRange() : null;
		if (savedRange != null) {
			double min = savedRange.getMin();
            double max = savedRange.getMax();
			var profileSettings = profileField.getProfileSettings();
			profileSettings.hpage = (int)max - (int)min;
			profileSettings.setLayer((int)min);
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

    public void updateScroll() {
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
		}

		if (cachedBufferedImage == null || cachedWidth != width || cachedHeight != height) {
			cachedBufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			cachedBuffer = ((DataBufferInt) cachedBufferedImage.getRaster().getDataBuffer()).getData();
			cachedWidth = width;
			cachedHeight = height;
		}

		Arrays.fill(cachedBuffer, BACK_GROUD_COLOR.getRGB());

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
        prismDrawer.draw(width, this, g2, cachedBuffer, getRealContrast());
        g2.drawImage(cachedBufferedImage, 0, 0, width, height, null);

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
        g2.drawLine(-width / 2, y, width / 2, y);

        int bottomSelectedSmp = profileSettings.getLayer() + profileSettings.hpage;
        int y2 = (int) traceSampleToScreen(new TraceSample(
                0, bottomSelectedSmp)).getY();

        g2.drawLine(-width / 2, y2, width / 2, y2);
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
        NavigableMap<Integer, IndexRange> lineRanges = getFile().getLineRanges();
        for (IndexRange range : lineRanges.values()) {
            RemoveLineButton removeLine = new RemoveLineButton(
                    new TraceKey(file, range.from()),
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

    @Override
    public void setTraceUnit(TraceUnit traceUnit) {
        horizontalRulerController.setUnit(traceUnit);
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

    private void drawHorizontalProfile(Graphics2D g2, HorizontalProfile pf, int voffset) {
        g2.setColor(pf.getColor());
		int trace1 = 0;
		int sample1 = pf.getDepth(0) + voffset;
		int x1 = traceToScreen(trace1) + (int) (getHScale() / 2);
		int y1 = sampleToScreen(sample1) + (int) (getVScale() / 2);

		for (int i = 1; i < pf.size(); i++) {
			int sample2 = pf.getDepth(i) + voffset;
			int x2 = traceToScreen(i) + (int) (getHScale() / 2);
			int y2 = sampleToScreen(sample2) + (int) (getVScale() / 2);

			if (x2 - x1 > 0 || Math.abs(y2 - y1) > 0) {
				g2.drawLine(x1, y1, x2, y2);
				x1 = x2;
				y1 = y2;
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

        NavigableMap<Integer, IndexRange> lineRanges = getFile().getLineRanges();
        for (Map.Entry<Integer, IndexRange> e : lineRanges.entrySet()) {
            Integer lineIndex = e.getKey();
            IndexRange lineRange = e.getValue();

            if (lineIndex == selectedLineIndex) {
                g2.setStroke(LEVEL_STROKE);
                g2.setColor(new Color(0, 120, 215));
                g2.setFont(fontB);
            } else {
                g2.setStroke(AMP_STROKE);
                g2.setColor(new Color(234, 51, 35));
                g2.setFont(fontP);
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
			new EventHandler<>() {
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

    private final EventHandler<MouseEvent> mouseReleaseHandler =
            new EventHandler<>() {
                @Override
                public void handle(MouseEvent event) {
                    Point2D p = getLocalCoords(event);
                    if (selectedMouseHandler != null) {
                        selectedMouseHandler.mouseReleaseHandle(p, GPRChart.this);
                        selectedMouseHandler = null;
                    }

					setAmplitudeRangeToMeta();
                }
            };

    protected EventHandler<MouseEvent> dragDetectedHandler =
			mouseEvent -> {
				canvas.startFullDrag();
				canvas.setCursor(Cursor.CLOSED_HAND);
			};

    protected EventHandler<MouseDragEvent> dragReleaseHandler =
			new EventHandler<>() {
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
			new EventHandler<>() {

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
    public int numTraces() {
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
        return Math.clamp(trace, 0, numTraces() - 1);
    }

    public int getLastVisibleTrace() {
        double x = profileField.getMainRect().width / 2.0;
        int trace = screenToTraceSample(new Point2D(x, 0)).getTrace();
        return Math.clamp(trace, 0, numTraces() - 1);
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
    public int numVisibleTraces() {
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
    public void selectTrace(@Nullable TraceKey trace, boolean focus) {
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

        int maxSamples = profileField.getMaxHeightInSamples();
        int numTraces = range.size();
        // as middle trace is an exact half of the viewport
        // num traces should be even to properly calculate horizontal scale factor
        numTraces &= ~1;

        setMiddleTrace(range.from() + numTraces / 2);
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

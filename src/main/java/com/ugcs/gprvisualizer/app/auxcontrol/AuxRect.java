package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.github.thecoldwine.sigrun.common.ext.AreaType;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;

import javafx.scene.control.ChoiceDialog;

@SuppressWarnings("unchecked")
public class AuxRect extends BaseObjectImpl {
	
	private static final Color maskColor = new Color(50, 0, 255, 70);
	private static final int TRACE_W = 40;
	private static final int SAMPLE_W = 20;

	private final VerticalCutPart offset;
	
	private BufferedImage img;
	private DragAnchor top;
	private DragAnchor bottom;
	private DragAnchor left;
	private DragAnchor right;
	private ToggleButton lock;
	private ToggleButton selectType;
	
	private int[] topCut;
	private int[] botCut;
	private boolean sideTop = true;
	private boolean locked = false;
	private AreaType type = AreaType.Hyperbola;
	
	public boolean saveTo(JSONObject json) {
		json.put("traceStart", left.getTrace());
		json.put("traceFinish", right.getTrace());
		json.put("sampleStart", top.getSample());
		json.put("sampleFinish", bottom.getSample());
		json.put("type", getType().toString());
		json.put("locked", locked);
		
		JSONArray arr = new JSONArray();
		for (int i : topCut) {
			arr.add(i);
		}		
		json.put("topCut", arr);
		
		JSONArray arr2 = new JSONArray();
		for (int i : botCut) {
			arr2.add(i);
		}		
		json.put("botCut", arr2);
		
		return true;
	}

	public void setSampleStart(int sampleStart) {
		top.setSample(sampleStart);
		//this.sampleStart = sampleStart;
	}

	public void setSampleFinish(int sampleFinish) {
		bottom.setSample(sampleFinish);
		//this.sampleFinish = sampleFinish;
	}
	
	public int getSampleStart() {
		return top.getSample();
	}

	public int getSampleFinish() {
		return bottom.getSample();
	}
	
	public void setTraceStart(int traceStart) {
		left.setTrace(traceStart);
	}

	public int getTraceStart() {
		return left.getTrace();
	}

	public void setTraceFinish(int traceStart) {
		right.setTrace(traceStart);
	}
	
	public int getTraceFinish() {
		return right.getTrace();
	}

	public AuxRect(
			int traceStart,
			int traceFinish,
			int sampleStart,
			int sampleFinish,
			VerticalCutPart offset) {

		this.offset = offset;
		initDragAnchors();
		
		setTraceStart(traceStart);
		setTraceFinish(traceFinish);
		setSampleStart(sampleStart);
		setSampleFinish(sampleFinish);

		clearCut();
		updateMaskImg();
	}

	public AuxRect(JSONObject json, VerticalCutPart offset) {
		this.offset = offset;
		initDragAnchors();
		
		setTraceStart((int) (long) (Long) json.get("traceStart"));
		setTraceFinish((int) (long) (Long) json.get("traceFinish"));
		setSampleStart((int) (long) (Long) json.get("sampleStart"));
		setSampleFinish((int) (long) (Long) json.get("sampleFinish"));
		
		setType(AreaType.valueOf((String) json.get("type")));
		if (json.containsKey("locked")) {
			locked = (Boolean) json.get("locked");
			updateAnchorVisibility();
			lock.setSelected(locked);
		}
		
		if (json.containsKey("topCut")) {
			JSONArray ar = (JSONArray) json.get("topCut");
	
			topCut = new int[ar.size()];
			for (int i = 0; i < ar.size(); i++) {
				
				topCut[i] = (int) (long) (Long) ar.get(i);
			}
		}

		if (json.containsKey("botCut")) {
			JSONArray ar = (JSONArray) json.get("botCut");
			botCut = new int[ar.size()];
			for (int i = 0; i < ar.size(); i++) {
				botCut[i] = (int) (long) (Long) ar.get(i);
			}
		}
		
		if (botCut == null || topCut == null) {
			clearCut();
		}

		updateMaskImg();
	}

	private void initDragAnchors() {
		selectType = new ToggleButton(ResourceImageHolder.IMG_CHOOSE,
				ResourceImageHolder.IMG_CHOOSE,
				new AlignRect(-1, -1), false) {

			public void signal(Object obj) {
		        ChoiceDialog<AreaType> dialog = 
		        		new ChoiceDialog<>(getType(), AreaType.values());
		        Optional<AreaType> result = dialog.showAndWait();
		        result.ifPresent(book -> {
		            setType(book);
		        });				
			}
			
			@Override
			public int getTrace() {
				return (left.getTrace());
			}
			
			@Override
			public int getSample() {
				return (top.getSample());
			}
		};
		
		lock = new ToggleButton(ResourceImageHolder.IMG_LOCK,
				ResourceImageHolder.IMG_UNLOCK,
				new AlignRect(-1, -1), locked) {

			public void signal(Object obj) {
				locked = (Boolean) obj;
				
				updateAnchorVisibility();
			}
			
			public int getTrace() {
				return (right.getTrace());
			}
			
			public int getSample() {
				return (top.getSample());
			}			
		};
		
		top = new DragAnchor(ResourceImageHolder.IMG_VER_SLIDER, AlignRect.CENTER) {
			public void signal(Object obj) {
				
				top.setSample(Math.clamp(
						top.getSample(), 0, bottom.getSample() - 2));
				
				clearCut();
				updateMaskImg();
			}
			
			public int getTrace() {
				return (left.getTrace() + right.getTrace()) / 2;
			}
		};
		
		bottom = new DragAnchor(ResourceImageHolder.IMG_VER_SLIDER, AlignRect.CENTER) {
			public void signal(Object obj) {

				bottom.setSample(Math.clamp(bottom.getSample(),
					top.getSample() + 2, offset.getTraces() - 1));
				
				clearCut();
				updateMaskImg();
			}
			
			public int getTrace() {
				return (left.getTrace() + right.getTrace()) / 2;
			}
		};
		
		left = new DragAnchor(ResourceImageHolder.IMG_HOR_SLIDER, AlignRect.CENTER) {
			public void signal(Object obj) {

				left.setTrace(Math.clamp(left.getTrace(),
						0, right.getTrace() - 2));
				
				clearCut();
				updateMaskImg();
			}
			
			public int getSample() {
				return (top.getSample() + bottom.getSample()) / 2;
			}
		};
		
		right = new DragAnchor(ResourceImageHolder.IMG_HOR_SLIDER, AlignRect.CENTER) {
			public void signal(Object obj) {

				right.setTrace(
						Math.clamp(right.getTrace(),
							left.getTrace() + 2, offset.getMaxSamples() - 1));
				
				clearCut();
				updateMaskImg();				
			}
			
			public int getSample() {
				return (top.getSample() + bottom.getSample()) / 2;
			}
		};
		
		lock.signal(locked);
	}

	public void updateMaskImg() {
		
		int width = getTraceFinish() - getTraceStart();
		int height = getSampleHeight();
		
		img = new BufferedImage(Math.max(1, width), Math.max(1, height), 
				BufferedImage.TYPE_4BYTE_ABGR);
		
		Graphics2D g2 = (Graphics2D) img.getGraphics();
		
		for (int x = 0; x < topCut.length; x++) {
			
			g2.setColor(maskColor);
			g2.drawLine(x, 0, x, topCut[x]);
			g2.drawLine(x, img.getHeight(), x, botCut[x]);
		}
	}

	private void clearCut() {
		
		int width = Math.max(1, getTraceFinish() - getTraceStart());
		int height = getSampleHeight();

		topCut = new int[width];
		botCut = new int[width];
		Arrays.fill(botCut, height);
	}
	
	public List<BaseObject> getControls() {
		return List.of(left, top, right, bottom, lock, selectType);
	}

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {

		if (isPointInside(localPoint, profField)) {			
		
			TraceSample ts = profField.screenToTraceSample(localPoint); //, offset);
			int x = ts.getTrace() - getTraceStart();
			int y = ts.getSample() - getSampleStart();
			if (x >= 0 && x < topCut.length) {
				
				int topDst = Math.abs(topCut[x] - y);
				int botDst = Math.abs(botCut[x] - y);

				sideTop = topDst < botDst;
				lastX = -1;
			}
			return true;
		}
		
		return false;
	}

	private int lastX = -1;
	
	@Override
	public boolean mouseMoveHandle(Point2D point, ScrollableData profField) {
		if (img == null) {
			return false;
		}
		if (!(profField instanceof GPRChart)) {
			return false;
		}
		GPRChart gprChart = (GPRChart)profField;
		int numTraces = gprChart.getTracesCount();
		int maxSamples = gprChart.getField().getMaxHeightInSamples();

		TraceSample ts = profField.screenToTraceSample(point);
		
		if (locked) {		
			drawCutOnImg(ts);
		} else {
			
			int halfWidth = (getTraceFinish() - getTraceStart()) / 2;
			int halfHeight = getSampleHeight() / 2;
			
			int tr = Math.clamp(ts.getTrace(),
					halfWidth, numTraces - halfWidth);
			
			int sm = Math.clamp(ts.getSample(),
					halfHeight, maxSamples - halfHeight);
			
			setTraceStart(tr - halfWidth);
			setTraceFinish(tr + halfWidth);
			setSampleStart(sm - halfHeight);
			setSampleFinish(sm + halfHeight);
		}
		
		return true;
	}

	private void drawCutOnImg(TraceSample ts) {
		int x = ts.getTrace() - getTraceStart();
		int y = ts.getSample() - getSampleStart();
		
		int height = getSampleHeight();
		y = Math.max(Math.min(y, height), 0);
		
		if (x >= 0 && x < topCut.length) {
			if (lastX == -1 || Math.abs(lastX - x) > 12) {
				lastX = x;
			}
			
			for (int i = Math.min(lastX, x); i <= Math.max(lastX, x); i++) {
				
				drawColumn(i, y);
			}
			
			lastX = x;
		}
	}

	private int getSampleHeight() {
		return getSampleFinish() - getSampleStart();
	}

	private void drawColumn(int x, int y) {
		if (sideTop) {
			topCut[x] = y;
		} else {
			botCut[x] = y;
		}

		Graphics2D g2 = (Graphics2D) img.getGraphics();
		
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR));
		g2.setColor(new Color(0, 0, 0, 0));
		g2.drawLine(x, img.getHeight(), x, 0);
		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
		
		g2.setColor(maskColor);
		g2.drawLine(x, 0, x, topCut[x]);
		g2.drawLine(x, img.getHeight(), x, botCut[x]);
	}

	public Rectangle getRect(ScrollableData profField) {
		var lt = profField.traceSampleToScreen(
				new TraceSample(getTraceStart(), getSampleStart()));
		
		var rb = profField.traceSampleToScreen(
				new TraceSample(getTraceFinish(), getSampleFinish()));
		return new Rectangle((int) lt.getX(), (int) lt.getY(), (int) (rb.getX() - lt.getY()), (int) (rb.getY() - lt.getY()));
	}
	
	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
		if (scrollableData instanceof GPRChart gprChart) {
			var profField = gprChart.getField();
			setClip(g2, profField.getClipMainRect());

			Rectangle rect = getRect(scrollableData);

			if (img != null) {
				g2.drawImage(img, rect.x, rect.y, rect.width, rect.height, null);
			}

			g2.setColor(Color.RED);
			g2.drawRect(rect.x, rect.y, rect.width, rect.height);

			g2.setColor(Color.WHITE);
			g2.drawString(getType().getName(), rect.x, rect.y - 5);
		}
	}

	@Override
	public boolean isPointInside(Point2D localPoint, ScrollableData profField) {
		Rectangle rect = getRect(profField);
		return rect.contains(localPoint.getX(), localPoint.getY());
	}

	public AreaType getType() {
		return type;
	}

	public void setType(AreaType type) {
		this.type = type;
	}

	public void setTopCut(int[] topCut) {
		this.topCut = topCut;
	}
	
	public void setBotCut(int[] botCut) {
		this.botCut = botCut;
	}

	private void updateAnchorVisibility() {
		left.setVisible(!locked); 
		top.setVisible(!locked);
		right.setVisible(!locked);
		bottom.setVisible(!locked);
	}			

	@Override
	public BaseObject copy(int traceOffset, VerticalCutPart verticalCutPart) {
		AuxRect result = new AuxRect(
				getTraceStart() - traceOffset,
				getTraceFinish() - traceOffset,
				getSampleStart(), 
				getSampleFinish(), 
				verticalCutPart); 
		
		result.topCut = Arrays.copyOf(topCut, topCut.length); 
		result.botCut = Arrays.copyOf(botCut, botCut.length);
		result.updateMaskImg();
		
		return result;
	}

	@Override
	public boolean isFit(int begin, int end) {
		
		return getTraceStart() >= begin && getTraceFinish() <= end;
	}
	
}

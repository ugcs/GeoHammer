package com.ugcs.geohammer.chart.gpr;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.Settings;

public class ProfileField {

	private final TraceFile traceFile;

	// screen coordinates
	private Dimension viewDimension = new Dimension();
	private Rectangle topRuleRect = new Rectangle();
	private Rectangle leftRuleRect = new Rectangle();
	private Rectangle infoRect = new Rectangle();
	private Rectangle mainRect = new Rectangle();
	private Rectangle bottomRuleRect = new Rectangle();

	//draw coordinates
	private Rectangle clipMainRect = new Rectangle();
	private Rectangle clipLeftMainRect = new Rectangle();
	private Rectangle clipTopMainRect = new Rectangle();
	private Rectangle clipInfoRect = new Rectangle();

	//
	private int visibleStart;
	//private int visibleFinish;

	private int maxHeightInSamples = 0;
	private final Settings profileSettings = new Settings();

	public int getMaxHeightInSamples() {
		return maxHeightInSamples;
	}

	public void updateMaxHeightInSamples() {
		// set index of traces
		int maxHeight = 0;
		for (Trace trace: getGprTraces()) {
			maxHeight = Math.max(maxHeight, trace.numSamples());
		}

		this.maxHeightInSamples = maxHeight;
		getProfileSettings().maxsamples = maxHeightInSamples;

		if (getProfileSettings().getLayer() + getProfileSettings().hpage > maxHeightInSamples) {
			getProfileSettings().setLayer(maxHeightInSamples / 4);
			getProfileSettings().hpage = maxHeightInSamples / 4;
		}
	}

	public Settings getProfileSettings() {
		return profileSettings;
	}

	public List<Trace> getGprTraces() {
		return traceFile.getTraces();
	}

	public int getGprTracesCount() {
		return getGprTraces().size();
	}

	public ProfileField(TraceFile traceFile) {
		this.traceFile = traceFile;

		updateMaxHeightInSamples();
	}

	public int getVisibleStart() {
		return visibleStart;
	}

	public Dimension getViewDimension() {
		return viewDimension;
	}

	public void setViewDimension(Dimension viewDimension) {
		this.viewDimension = viewDimension;

		int leftMargin = 30;
		int ruleWidth = 90;
		int infoWidth = 50;
		int bottomRuleHeight = 65;

		topRuleRect = new Rectangle(
				leftMargin,
				0,
				viewDimension.width - leftMargin - ruleWidth,
				Model.TOP_MARGIN - 1);
		infoRect = new Rectangle(
				leftMargin + topRuleRect.width,
				0,
				infoWidth,
				Model.TOP_MARGIN - 1);
		leftRuleRect = new Rectangle(
				leftMargin + topRuleRect.width,
				Model.TOP_MARGIN,
				ruleWidth,
				viewDimension.height - Model.TOP_MARGIN);
		mainRect = new Rectangle(
				leftMargin,
				Model.TOP_MARGIN,
				viewDimension.width - leftMargin - ruleWidth,
				viewDimension.height - Model.TOP_MARGIN - bottomRuleHeight);

		bottomRuleRect = new Rectangle(
				leftMargin,
				viewDimension.height - bottomRuleHeight,
				viewDimension.width - leftMargin - ruleWidth,
				bottomRuleHeight
		);


		visibleStart = -mainRect.x - mainRect.width / 2;

		initClipRects();
 	}

	public Rectangle getTopRuleRect() {
		return topRuleRect;
	}

	public Rectangle getLeftRuleRect() {
		return leftRuleRect;
	}

	public Rectangle getMainRect() {
		return mainRect;
	}

	public Rectangle getInfoRect() {
		return infoRect;
	}

	public Rectangle getClipMainRect() {
		return clipMainRect;		
	}

	public Rectangle getClipLeftMainRect() {
		return clipLeftMainRect;
	}

	public Rectangle getClipTopMainRect() {
		return clipTopMainRect;
	}

	public Rectangle getClipInfoRect() {
		return clipInfoRect;
	}

	public Rectangle getBottomRuleRect() {
		return bottomRuleRect;
	}

	public void initClipRects() {
		clipMainRect = new Rectangle(
				-getMainRect().width / 2, getMainRect().y, 
				getMainRect().width, getMainRect().height);

		clipTopMainRect = new Rectangle(
				-getMainRect().width / 2, 0, 
				getMainRect().width, getMainRect().y + getMainRect().height);

		clipLeftMainRect = new Rectangle(
				-getMainRect().x - getMainRect().width / 2, getMainRect().y, 
				getMainRect().x + getMainRect().width, getMainRect().height);

		clipInfoRect = new Rectangle(
				-getMainRect().x - getMainRect().width / 2, 0, getInfoRect().width, getInfoRect().height);
	}

	public int getTopMargin() {
		return mainRect.y;
	}

	public TraceFile getFile() {
		return traceFile;
	}
}

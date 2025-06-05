package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.Settings;
import org.jspecify.annotations.NonNull;

public class ProfileField {

	private final List<TraceFile> traceFiles = new ArrayList<>();

	// screen coordinates
	private Dimension viewDimension = new Dimension();
	private Rectangle topRuleRect = new Rectangle();
	private Rectangle leftRuleRect = new Rectangle();
	private Rectangle infoRect = new Rectangle();
	private Rectangle mainRectRect = new Rectangle();

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

	private void updateMaxHeightInSamples() {

		//set index of traces
		int maxHeight = 0;
		for (Trace tr: getGprTraces()) {
			maxHeight = Math.max(maxHeight, tr.getNormValues().length);
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

	List<Trace> gprTraces = new java.util.ArrayList<>();

	public List<Trace> getGprTraces() {
		if (gprTraces.isEmpty()) {
			int traceIndex = 0;
			for (TraceFile file : traceFiles) {
				for (Trace trace : file.getTraces()) {
					gprTraces.add(trace);
					trace.setIndexInSet(traceIndex++);
				}
			}
		}
		return gprTraces;
	}

	public int getGprTracesCount() {
		return getGprTraces().size();
	}

	public ProfileField(List<TraceFile> traceFiles) {
		this.traceFiles.addAll(traceFiles);
		updateMaxHeightInSamples();
		updateSgyFilesOffsets();
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

		topRuleRect = new Rectangle(leftMargin, 0, viewDimension.width - leftMargin - ruleWidth, Model.TOP_MARGIN - 1);
		infoRect = new Rectangle(leftMargin + topRuleRect.width, 0, Model.TOP_MARGIN - 1, Model.TOP_MARGIN - 1);
		leftRuleRect = new Rectangle(leftMargin + topRuleRect.width, Model.TOP_MARGIN, ruleWidth, viewDimension.height - leftMargin);
		mainRectRect = new Rectangle(leftMargin, Model.TOP_MARGIN, viewDimension.width - leftMargin - ruleWidth, viewDimension.height - leftMargin);

		visibleStart = -mainRectRect.x -mainRectRect.width / 2;

		initClipRects();
 	}

	public Rectangle getTopRuleRect() {
		return topRuleRect;
	}

	public Rectangle getLeftRuleRect() {
		return leftRuleRect;
	}

	public Rectangle getMainRect() {
		return mainRectRect;
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
		return mainRectRect.y;
	}

	public List<TraceFile> getSgyFiles() {
		return List.copyOf(traceFiles);
	}

	public TraceFile getSgyFileByTrace(int i) {
		for (TraceFile fl : getSgyFiles()) {
			Trace lastTrace = fl.getTraces().get(fl.getTraces().size() - 1);
			if (i <= lastTrace.getIndexInSet()) {
				return fl;
			}
		}
		return null;
	}

	public int getSgyFileIndexByTrace(int i) {
		for (int index = 0;
			 index < getSgyFiles().size(); index++) {
			TraceFile fl =  getSgyFiles().get(index);

			if (i <= fl.getTraces().get(fl.getTraces().size() - 1).getIndexInSet()) {
				return index;
			}
		}
		return 0;
	}

	public void addSgyFile(@NonNull TraceFile f) {
		traceFiles.add(f);
		traceFiles.sort((f1, f2) -> {
			return f1.getFile().getName().compareToIgnoreCase(f2.getFile().getName());
		});
		gprTraces.clear();
		updateMaxHeightInSamples();
		updateSgyFilesOffsets();
	}

	public void removeSgyFile(TraceFile closedFile) {
		traceFiles.remove(closedFile);
		gprTraces.clear();
		updateMaxHeightInSamples();
		updateSgyFilesOffsets();
	}

	private void updateSgyFilesOffsets() {
		int startTraceNum = 0;
		for (TraceFile sgyFile : getSgyFiles()) {
			sgyFile.getOffset().setStartTrace(startTraceNum);
			startTraceNum += sgyFile.getTraces().size();
			sgyFile.getOffset().setFinishTrace(startTraceNum);
			sgyFile.getOffset().setMaxSamples(getMaxHeightInSamples());
		}
	}

	public List<TraceFile> getFilesInRange(int startTrace, int finishTrace) {
		int f1 = getSgyFiles().indexOf(getSgyFileByTrace(startTrace));
		int f2 = getSgyFiles().indexOf(getSgyFileByTrace(finishTrace));

		List<TraceFile> result = new ArrayList<>();
		for (int i = f1; i <= f2; i++) {
			result.add(getSgyFiles().get(i));
		}
		return result;
	}

	public TraceFile getNextSgyFile(TraceFile selectedFile) {
		var index = traceFiles.indexOf(selectedFile);
		index = Math.min(traceFiles.size() - 1, index + 1);
		return traceFiles.get(index);
	}

	public TraceFile getPrevSgyFile(TraceFile selectedFile) {
		var index = traceFiles.indexOf(selectedFile);
		index = Math.max(0, index - 1);
		return traceFiles.get(index);
	}
}

package com.ugcs.geohammer.chart.gpr;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.Settings;

public class PrismDrawer {

	static final int OPACITY_MASK = 0xff << 24;

	private Model model;
	private Tanh tanh = new Tanh();
	
	public PrismDrawer(Model model) {
		this.model = model;
	}

	public void draw(
			int bytesInRow, 
			GPRChart field,
			Graphics2D g2,
			int[] buffer,			
			double threshold) {
		
		if (model.isLoading() || !model.getFileManager().isActive()) {
			return;
		}
		
		Rectangle rect = field.getField().getMainRect();

		List<Trace> traces = field.getField().getGprTraces();
		
		tanh.setThreshold((float) threshold);
		
		int startTrace = field.getFirstVisibleTrace();
		int finishTrace = field.getLastVisibleTrace();
		int lastSample = field.getLastVisibleSample();

        int baseOffsetX = rect.x + rect.width / 2;

		Settings profileSettings = field.getField().getSettings();
		float middleAmp = profileSettings.getMiddleAmplitude();

		for (int i = startTrace; i <= finishTrace; i++) {
			if (i < 0 || i >= traces.size()) {
				continue;
			}

			int traceStartX = field.traceToScreen(i);
			int traceFinishX = field.traceToScreen(i + 1);
			int hscale = traceFinishX - traceStartX;
			if (hscale < 1) {
				continue;
			}
			
			Trace trace = traces.get(i);

			for (int j = field.getStartSample();
                 j < Math.min(lastSample, trace.numSamples()); j++) {
				
				int sampStart = field.sampleToScreen(j);
				int sampFinish = field.sampleToScreen(j + 1);
				
				int vscale = sampFinish - sampStart;
				if (vscale == 0) {
					continue;
				}
				
				if (j < 0 || j >= trace.numSamples()) {
					continue;
				}
				float v = trace.getSample(j);
				int color = tanh.trans(v - middleAmp);
				
                int baseIndex = baseOffsetX + traceStartX + sampStart * bytesInRow;
                for (int yt = 0; yt < vscale; yt++) {
                    int rowStart = baseIndex + yt * bytesInRow;
                    Arrays.fill(buffer, rowStart, rowStart + hscale, color | OPACITY_MASK);
                }
			}
		}
	}	
}

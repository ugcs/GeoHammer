package com.ugcs.geohammer.chart.gpr;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.Settings;

public class PrismDrawer {

	private Model model;
	private Tanh tanh = new Tanh();
	
	int goodcolor1 = (250 << 16) + (250 << 8) + 32;
	int goodcolor2 = (70 << 16) + (235 << 8) + 197;
	
	int colorRed3 = (120 << 16) + (0 << 8) + 0;
	int colorBlue4 = (255 << 16) + (130 << 8) + 130;
	
	int geencolorp = (0 << 16) + (100 << 8) + 94;
	int geencolorm = (94 << 16) + (100 << 8) + 0;	
	int geencolorb = (90 << 16) + (250 << 8) + 90;
	
	public PrismDrawer(Model model) {
		this.model = model;
	}

	int[] goodColors = {
			0,
			geencolorp,
			geencolorm,
			geencolorb
	};
	
	int[] edgeColors = {
			
			0,
			goodcolor1,
			goodcolor2,			
			colorBlue4,
			colorRed3
	};
	
	public void draw(//int width, int height, 
			int bytesInRow, 
			GPRChart field,
			Graphics2D g2,
			int[] buffer,			
			double threshold) {
		
		if (model.isLoading() || !model.getFileManager().isActive()) {
			return;
		}
		
		Rectangle rect = field.getField().getMainRect();

		Settings profileSettings = field.getField().getProfileSettings();
		boolean showInlineHyperbolas = profileSettings.showGood.booleanValue();
		boolean showEdge = profileSettings.showEdge.booleanValue();

		List<Trace> traces = field.getField().getGprTraces();
		
		tanh.setThreshold((float) threshold);
		
		int startTrace = field.getFirstVisibleTrace();
		int finishTrace = field.getLastVisibleTrace();
		int lastSample = field.getLastVisibleSample();

        int baseOffsetX = rect.x + rect.width / 2;

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
			float middleAmp = profileSettings.hypermiddleamp;

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
				
				if (showEdge && trace.getEdge(j) > 0) {
					color = edgeColors[trace.getEdge(j)];
				}

				if (showInlineHyperbolas && trace.getGood(j) > 0) {
					color = goodColors[trace.getGood(j)];
				}

                int baseIndex = baseOffsetX + traceStartX + sampStart * bytesInRow;
                for (int yt = 0; yt < vscale; yt++) {
                    int rowStart = baseIndex + yt * bytesInRow;
                    Arrays.fill(buffer, rowStart, rowStart + hscale, color);
                }
			}
		}
	}	
}

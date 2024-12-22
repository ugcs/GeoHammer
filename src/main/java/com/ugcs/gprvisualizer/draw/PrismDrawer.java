package com.ugcs.gprvisualizer.draw;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.gpr.Settings;
import com.ugcs.gprvisualizer.math.HorizontalProfile;

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
			ProfileField field,
			Graphics2D g2,
			int[] buffer,			
			double threshold) {
		
		if (model.isLoading() || !model.getFileManager().isActive()) {
			return;
		}
		
		Rectangle rect = field.getMainRect();

		Settings profileSettings = field.getProfileSettings();
		boolean showInlineHyperbolas = profileSettings.showGood.booleanValue();
		boolean showEdge = profileSettings.showEdge.booleanValue();
		boolean shiftGround = profileSettings.levelPreview.booleanValue();
		
		HorizontalProfile gp = model.getFileManager().getGprFiles().get(0).getGroundProfile();
		
		List<Trace> traces = field.getGprTraces();
		
		tanh.setThreshold((float) threshold);
		
		int startTrace = field.getFirstVisibleTrace();
		int finishTrace = field.getLastVisibleTrace();
		int lastSample = field.getLastVisibleSample(rect.height);
		
		for (int i = startTrace; i < finishTrace; i++) {

			int traceStartX = field.traceToScreen(i);
			int traceFinishX = field.traceToScreen(i + 1);
			int hscale = traceFinishX - traceStartX;
			if (hscale < 1) {
				continue;
			}
			
			Trace trace = traces.get(i);
			float middleAmp = profileSettings.hypermiddleamp;
			float[] values = trace.getNormValues();
			
			int horshift = profileSettings.levelPreviewShift.intValue();
			int gpi = i + horshift;
			int vertshift = shiftGround && gp != null && gpi >=0 && gpi < gp.deep.length ? gp.deep[i + horshift] - gp.avgdeep : 0;
			
			
			for (int j = field.getStartSample();
					j < Math.min(lastSample, values.length); j++) {
				
				int sampStart = field.sampleToScreen(j);
				int sampFinish = field.sampleToScreen(j + 1);
				
				int vscale = sampFinish - sampStart;
				if (vscale == 0) {
					continue;
				}
				
				int z = j + vertshift;
				if (z < 0 || z >= values.length) {
					continue;
				}
				float v = values[z];
				int color = tanh.trans(v - middleAmp);
				
				if (showEdge && trace.edge != null && trace.edge[j] > 0) {
					color = edgeColors[trace.edge[j]];
				}

				if (showInlineHyperbolas 
						&& trace.good != null 
						&& trace.good[j] > 0) {
					color = goodColors[trace.good[j]];
				}
				
	    		for (int xt = 0; xt < hscale; xt++) {
	    			for (int yt = 0; yt < vscale; yt++) {
	    				int index = rect.x + rect.width / 2 + xt + traceStartX 
	    						+ (sampStart + yt) * bytesInRow;
						buffer[index ] = color;
	    			}
	    		}
			}
		}
	}	
}

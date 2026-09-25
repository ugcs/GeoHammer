package com.ugcs.geohammer.chart.gpr;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.chart.tool.projection.math.DbGain;
import com.ugcs.geohammer.format.SampleStatistics;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.palette.Palettes;
import com.ugcs.geohammer.service.palette.Spectrum;

public class PrismDrawer {

	static final int OPACITY_MASK = 0xff << 24;

	private Model model;
	
	public PrismDrawer(Model model) {
		this.model = model;
	}

	public void draw(
			int bytesInRow, 
			GPRChart field,
			Graphics2D g2,
			int[] buffer) {
		
		if (model.isLoading() || !model.getFileManager().isActive()) {
			return;
		}
		
		Rectangle rect = field.getField().getMainRect();

		List<Trace> traces = field.getField().getGprTraces();
		
		TraceFile file = field.getField().getFile();
		SampleStatistics stats = file.getStatistics();
		ProfileSettings profileSettings = field.getField().getSettings();

		Spectrum spectrum = Palettes.createSpectrum(profileSettings.getColorScale());
		int[] colorTable = ContrastCurve.createColorTable(spectrum);
		ContrastCurve curve = new ContrastCurve(stats.dispersion(), profileSettings.getContrast());
		DbGain gainFunction = new DbGain(0, profileSettings.getMaxGain());
		float depthStep = field.numSamples() > 0 ? 1f / field.numSamples() : 0f;

		float baseline = stats.baseline();
		
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
				float value = trace.getSample(j) - baseline;
				value *= gainFunction.getGain(depthStep * j);
				int color = curve.mapToColor(value, colorTable);
				
                int baseIndex = baseOffsetX + traceStartX + sampStart * bytesInRow;
                for (int yt = 0; yt < vscale; yt++) {
                    int rowStart = baseIndex + yt * bytesInRow;
                    Arrays.fill(buffer, rowStart, rowStart + hscale, color | OPACITY_MASK);
                }
			}
		}
	}	
}

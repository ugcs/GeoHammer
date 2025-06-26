package com.ugcs.gprvisualizer.math;

import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.gpr.Model;

/*
 * find ground by set avg profile on diffrent heights 
 * and select those with smallest difference.
 *  not used.
 */
public class AvgShiftFilter {

	Model model;
	
	static final int START = 50;  
	static final int FINISH = 360;
	
	static final int RANGE = 3;

	float[] sumvalues = new float[FINISH];
	int cnt = 1;
	
	public AvgShiftFilter(Model model) {
		this.model = model;
	}
	
	public void execute2() {
		for (TraceFile sf : model.getFileManager().getGprFiles()) {
			execute2(sf.getTraces());
			
		}
	}
	
	public void execute2(List<Trace> traces) {
		sumvalues = new float[FINISH];
		cnt = 1;
		
		execute(traces);
		
		float[] avgvalues = getAvg();
		
		for (Trace tr : traces) {
			for (int i = 0; i < avgvalues.length; i++) {
				int avind = i - tr.getMaxIndex();
				if (avind >= 0 && avind < avgvalues.length) {
					float value = tr.getSample(i) - avgvalues[avind];
					tr.setSample(i, value);
				}
			}			
		}
	}
	
	public void execute() {
		for (TraceFile sf : model.getFileManager().getGprFiles()) {
			execute(sf.getTraces());			
		}		
	}
	
	public void execute(List<Trace> traces) {
		
		Trace fsttr = traces.getFirst();
		//int length = fsttr.getNormValues().length;
	
		addToAvg(sumvalues, fsttr, 0);
		
		int shift = 0;
		
		for (Trace tr : traces) {
			
			float[] avgvalues = getAvg();
			
			
			shift = lessDiff(tr, avgvalues, shift);
			
			addToAvg(sumvalues, tr, shift);
			cnt++;
			
			tr.setMaxIndex(shift);
			//tr.maxindex2 = shift + START+RANGE; 
		}
	}
	
	private void addToAvg(float[] sumvalues, Trace trace, int shift) {

		for (int i = Math.max(START, START - shift);
				i < Math.min(FINISH, FINISH - shift); i++) {
			sumvalues[i] += trace.getSample(i + shift);
		}		
	}
	
	private float[] getAvg() {
		float[] avg = new float[sumvalues.length];
		
		for (int i = START; i < FINISH; i++) {
			avg[i] = sumvalues[i] / cnt;
		}
		
		return avg;
	}
	
	private int lessDiff(Trace trace, float[] avgvalues, int prevshift) {
		
		int from = prevshift - RANGE;
		float lessdiff = getDiff(trace, from, avgvalues);
		int lesshift = from;
		
		for (int shift = from; shift <= prevshift + RANGE; shift++) {
			
			//float[] shiftValues = new float[avgvalues.length];
			
			float diff = getDiff(trace, shift, avgvalues);
			if (Math.abs(diff) < Math.abs(lessdiff)) {
				lessdiff = diff;
				lesshift = shift;				
			}
		}
		
		return lesshift;
	}
	
	private float getDiff(Trace trace, int shift, float[] avgvalues) {
		float diff = 0;
		for (int i = Math.max(START, START - shift);
				i < Math.min(FINISH, FINISH - shift); i++) {
			
			diff += Math.abs(avgvalues[i] - trace.getSample(i + shift));
		}
		return diff;
	}	
}

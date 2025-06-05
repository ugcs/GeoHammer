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

			float[] values = tr.getNormValues();
			
			for (int i = 0; i < avgvalues.length; i++) {
				
				int avind = i - tr.maxindex;
				if (avind >= 0 && avind < avgvalues.length) {
					values[i] -= avgvalues[avind];
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
		
		Trace fsttr = traces.get(0);
		//int length = fsttr.getNormValues().length;
	
		addToAvg(sumvalues, fsttr.getNormValues(), 0);
		
		int shift = 0;
		
		for (Trace tr : traces) {
			
			float[] avgvalues = getAvg();
			
			
			shift = lessDiff(tr.getNormValues(), avgvalues, shift);
			
			addToAvg(sumvalues, tr.getNormValues(), shift);
			cnt++;
			
			tr.maxindex = shift;
			//tr.maxindex2 = shift + START+RANGE; 
		}
		
		
		
	}
	
	private void addToAvg(float[] sumvalues, float[] normValues, int shift) {

		for (int i = Math.max(START, START - shift);
				i < Math.min(FINISH, FINISH - shift); i++) {
			sumvalues[i] += normValues[i + shift]; 			
		}		
	}
	
	private float[] getAvg() {
		float[] avg = new float[sumvalues.length];
		
		for (int i = START; i < FINISH; i++) {
			avg[i] = sumvalues[i] / cnt;
		}
		
		return avg;
	}
	
	private int lessDiff(float[] normValues, float[] avgvalues, int prevshift) {
		
		int from = prevshift - RANGE;
		float lessdiff = getDiff(normValues, from, avgvalues);
		int lesshift = from;
		
		for (int shift = from; shift <= prevshift + RANGE; shift++) {
			
			//float[] shiftValues = new float[avgvalues.length];
			
			float diff = getDiff(normValues, shift, avgvalues); 
			if (Math.abs(diff) < Math.abs(lessdiff)) {
				lessdiff = diff;
				lesshift = shift;				
			}
		}
		
		return lesshift;
	}
	
	private float getDiff(float[] normValues, int shift, float[] avgvalues) {
		float diff = 0;
		for (int i = Math.max(START, START - shift);
				i < Math.min(FINISH, FINISH - shift); i++) {
			
			diff += Math.abs(avgvalues[i] - normValues[i + shift]);
		}
		
		return diff;
	}	
	
}

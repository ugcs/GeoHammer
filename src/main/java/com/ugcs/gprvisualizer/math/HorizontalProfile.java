package com.ugcs.gprvisualizer.math;

import java.awt.Color;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;

public class HorizontalProfile {

	// in smp
	public int[] deep;
	public int[] originalDeep;

	public int minDeep;
	public int maxDeep;
	public int height;
	public double avgval;
	public int avgdeep;
	
	public Color color = new Color(50, 200, 255);
	
	public HorizontalProfile(int size) {
		deep = new int[size];
	}
	
	public void finish(List<Trace> list) {
		minDeep = deep[0];
		maxDeep = deep[0];
		
		double valsum = 0;
		long deepsum = 0;
		
		//smooth
		smoothLevel();
		
		//min, max, avg_val
		for (int i = 0; i < deep.length; i++) {
			
			minDeep = Math.min(deep[i], minDeep);
			maxDeep = Math.max(deep[i], maxDeep);
			
			int d = deep[i];
			
			if (list != null) {
				Trace trace = list.get(i);

				if (d >= 0 && d < trace.numSamples()) {
					valsum += trace.getSample(d);
				}
			}
			deepsum += d;
		}
		
		avgval = valsum / deep.length;
		avgdeep = (int) (deepsum / deep.length);
		height = maxDeep - minDeep;
	}
	
	private void smoothLevel() {
		int[] result = new int[deep.length];
		for (int i = 0; i < deep.length; i++) {
			result[i] = avg(i);
		}

		deep = result;
	}

	private static final int R = 7;
	private static final double DR = R;
	
	private int avg(int i) {
		
		int from = i - R;
		from = Math.max(0, from);
		int to = i + R;
		to = Math.min(to, deep.length - 1);
		double sum = 0;
		double cnt = 0;
		
		for (int j = from; j <= to; j++) {
			double kfx = (DR + j - i) / (DR * 2);
			double kf = kfx * kfx * (1 - kfx) * (1 - kfx); 
			
			sum += deep[j] * kf;
			cnt += kf;
		}
		
		return (int) Math.round(sum / cnt);
	}

    public void shift(int intValue) {
		if (originalDeep == null) {
			originalDeep = deep.clone();
		}
		int[] newDeep = new int[deep.length];
		for (int i = 0; i < deep.length; i++) {
			if (i + intValue >= 0 && i + intValue < deep.length) {
				newDeep[i + intValue] = originalDeep[i];
			} else {
				newDeep[i] = 0;
			}
		}
		this.deep = newDeep;
    }	
}

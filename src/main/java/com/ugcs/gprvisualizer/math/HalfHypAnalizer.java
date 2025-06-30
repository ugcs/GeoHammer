package com.ugcs.gprvisualizer.math;

import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;

public class HalfHypAnalizer extends HalfHyperDst {

	private static final int PARTS = 4;
	
	static class Avg {
		int sum = 0;
		int abssum = 0;
		int count = 0;
		
		void add(int dist) {
			sum += dist;
			abssum += Math.abs(dist);
			count++;
		}
		
		double avg() {
			if (count == 0) {
				return 0;
			}
			return (double) sum / (double) count;
		}

		double variance() {
			if (count == 0) {
				return 0;
			}
			return (double) abssum / (double) count;
		}		
	}
	
	public double analize(int percent) {
		if (defective) {
			return 0;
		}
		//
		int from = -2;
		int to = 2;
		
		//
		List<Trace> traces = sgyFile.getTraces();
		
		
		Avg[][] avg = instantiateAvg(PARTS);
		
		
		int checkedLength = length * percent / 100;
		int partLength = checkedLength / PARTS; 
		int minPartLength = partLength * 2 / 3;
		
			
		for (int i = 0; i < checkedLength; i++) {
			
			int index = pinnacleTrace + side * i;			
			Trace trace = traces.get(index);
			int s = smp[i];
				
			for (int j = from; j <= to; j++) {
				
				int edge = trace.getEdge(s + j);
		
				int part = i * PARTS / checkedLength;
				
				avg[edge][part].add(j);
				
			}
		}
		
		double bestCount = 0;
		for (int edge = 1; edge < 5; edge++) {
			bestCount = Math.max(bestCount, checkEdge(avg[edge], minPartLength));
		}		
		
		return (double) bestCount / (double) checkedLength;
	}
	
	double checkEdge(Avg[] avg, int minPartLength) {
		int count = 0;
		for (Avg a : avg) {
			
			if (a.count < minPartLength 
					||  Math.abs(a.avg()) > 0.9 
					|| a.variance() > 0.75) {
				return 0;
			}
			
			count += a.count;
		}
		
		return count;
	}

	public static Avg[][] instantiateAvg(int parts) {
		Avg[][]avg = new Avg[5][];		
		
		Arrays.setAll(avg, p -> {
			Avg[] a = new Avg[parts];
			
			Arrays.setAll(a, z -> new Avg());
			return a;
		});
		return avg;
	}
	
}

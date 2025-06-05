package com.ugcs.gprvisualizer.math;

import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.auxcontrol.RulerTool;

public class HalfHyperDst {
	
	//static final double THRESHOLD = 0.76;

	
	//double sampleToCm_air;
	//double sampleToCm_grn;
	
	TraceFile sgyFile;
	
	//int traceIndex;
	//Trace trace;
	double example;
	int pinnacleTrace;
	int pinnacleSmp;
	
	// +1 | -1
	int side;
	
	int length;
	int[] smp = new int[300];
	double hypergoodsize;
	
	boolean defective = false;
	
	public double analize(int percent) {

		if (sgyFile.getTraces().get(0).edge == null) {
			System.out.println("!!!! edge not prepared");
			return 0;
		}

		
		return Math.max(analize(percent, true), analize(percent, false));
	}
	
	public double analize(int percent, boolean higher) {
		if (defective) {
			return 0;
		}
		//
		int from = higher ? -1 : 0;
		int to = higher ? 0 : 1;
		
		//
		List<Trace> traces = sgyFile.getTraces();
		
		// sum[0] - above
		// sum[1] - same
		// sum[2] - below		
		int[][] sum = new int[3][5];
		
		int checkedLength = length * percent / 100;
		
		for (int j = from; j <= to; j++) {
			
			for (int i = 0; i < checkedLength; i++) {
				int index = pinnacleTrace + side * i;
				Trace trace = traces.get(index);
				
				int s = smp[i];
				
				sum[j + 1][trace.edge[s + j]]++;
			}
		}
		
		int[] ressum = new int[5];
		
		for (int i = 1; i < 5; i++) {
			
			int all = sum[0][i] + sum[1][i] + sum[2][i];
			//most points must be at the same line
			if (all / 2 < sum[1][i]) {
				ressum[i] = all;
			}
			
		}
		
		// find max edge count 
		int max = Arrays.stream(ressum).max().getAsInt();
		
		
		return (double) max / (double) checkedLength;
	}
	
	private double[] smpToDst(int smp) {
		
		int grndSmp = sgyFile.getGroundProfile() != null 
				? sgyFile.getGroundProfile().deep[pinnacleTrace] : 0;
		
		return smpToDst(sgyFile, smp, grndSmp);
	}
	
	public static double[] smpToDst(SgyFile sgyFile, int smp, int grnd) {
		
		double airSmp = Math.min(grnd, smp);
		double grnSmp = smp - airSmp;
		
		double airDst = airSmp * sgyFile.getSamplesToCmAir();
		double grnDst = grnSmp * sgyFile.getSamplesToCmGrn();
		
		return new double[]{airDst, grnDst};
	}
	
	public double getGoodSideDst(int smp) {
		return getGoodSideDstPin(sgyFile, pinnacleSmp, smp);
	}
	
	public static double getGoodSideDstPin(SgyFile file, int pinnacleTr, int smp) {
		int grndSmp = file.getGroundProfile() != null ? file.getGroundProfile().deep[pinnacleTr] : 0;
		
		return getGoodSideDstGrnd(file, smp, grndSmp);
	}
	
	public static double getGoodSideDstGrnd(SgyFile sgyFile, int smp, int grndSmp) {
		
		double[] r = smpToDst(sgyFile, smp, grndSmp);
		double ycm = r[0] + r[1];
		
		return (ycm * 0.41);
	}

	public static HalfHyperDst getHalfHyper(TraceFile sgyFile,
			int pnclTr, int pnclSmp, int side, double factorX) {
		
		List<Trace> traces = sgyFile.getTraces();
		Trace trace = traces.get(pnclTr);
		int maxSamplIndex = trace.getNormValues().length - 2;
		
		//HalfHyperDst hh = new HalfHyperDst();
		HalfHyperDst hh = new HalfHypAnalizer();
		
		hh.sgyFile = sgyFile;
		hh.pinnacleTrace = pnclTr;		
		hh.pinnacleSmp = pnclSmp;
		hh.side = side;
		//hh.sampleToCm_air = sgyFile.getSamplesToCmAir();
		//hh.sampleToCm_grn = sgyFile.getSamplesToCmGrn();
		
		//reduce size not so intensive 
		double goodsizefactor = (1 + (factorX - 1) / 2);
		hh.hypergoodsize = hh.getGoodSideDst(hh.pinnacleSmp) / goodsizefactor;
		
		int i = 0;
		int index = 0;
		//double r[] = hh.smpToDst(hh.pinnacle_smp);
		double vertDstCm = RulerTool.distanceCm(sgyFile, pnclTr, pnclTr, 0, hh.pinnacleSmp);
		double x = 0;
		
		while (Math.abs(x) < hh.hypergoodsize && i < 300) {
			index = pnclTr + side * i;
			if (index < 0 || index >= traces.size()) {
				hh.defective = true;
				break;
			}

			x += getXStep(side, traces, i, index) * factorX;
			
			
			double diagCm = Math.sqrt(x * x + vertDstCm * vertDstCm);
			
			int smp = RulerTool.diagonalToSmp(sgyFile, pnclTr, pnclSmp, diagCm);
			if (smp >= maxSamplIndex) {
				break;
			}
			hh.smp[i] = smp;
			hh.length = i;
			i++;
			
		}
		
		return hh;
	}


	/**
	 * in cm.
	 */
	public static double getXStep(int side, List<Trace> traces, int i, int index) {
		double xstep = 0;
		if (i > 0) {
			if (side > 0) {
				//dist to cm
				xstep = traces.get(index).getPrevDist();
			} else {
				xstep = traces.get(index + 1).getPrevDist();
			}
		}
		return xstep;
	}

	
}

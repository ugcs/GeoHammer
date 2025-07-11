package com.ugcs.gprvisualizer.math;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;

public class HalfHyperDst {
	
	public static double[] smpToDst(TraceFile traceFile, int smp, int grnd) {
		double airSmp = Math.min(grnd, smp);
		double grnSmp = smp - airSmp;
		double airDst = airSmp * traceFile.getSamplesToCmAir();
		double grnDst = grnSmp * traceFile.getSamplesToCmGrn();
		
		return new double[]{airDst, grnDst};
	}

	public static double getGoodSideDstGrnd(TraceFile traceFile, int smp, int grndSmp) {
		double[] r = smpToDst(traceFile, smp, grndSmp);
		double ycm = r[0] + r[1];
		
		return (ycm * 0.41);
	}
}

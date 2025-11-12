package com.ugcs.geohammer.math;

public class ArrayMath {
	public static void arraySum(float[] avg, float[] add) {
		for (int i = 0; i < avg.length && i < add.length; i++) {
			avg[i] += add[i];
		}
	}

	public static void arraySub(float[] avg, float[] add) {
		for (int i = 0; i < avg.length && i < add.length; i++) {
			avg[i] -= add[i];
		}
	}

	public static void arrayDiv(float[] avg, float divider) {
		for (int i = 0; i < avg.length; i++) {
			avg[i] /= divider;
		}
	}


}

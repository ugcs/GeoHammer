package com.ugcs.geohammer.util;

import java.util.ArrayList;
import java.util.List;

public class StretchArray {

	private List<Integer> list = new ArrayList<>();

	public void add(int val) {
		list.add(val);
	}
	
	public int[] stretchToArray(int size) {
		
		int[] result = new int[size];

		if (list.isEmpty()) {
			return result;
		}
		
		for (int i = 0; i < size; i++) {
			double srci = (double) i * (double) list.size() / (double) size;
			result[i] = list.get((int) srci);
		}
		
		return result;
	}
	
	public int size() {
		return list.size();
	}
}

package com.ugcs.gprvisualizer.app.commands;

import java.util.ArrayList;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.math.HorizontalProfile;

/**
 * find cohesive lines in edges.
 * 
 */
public class HorizontalGroupScan implements Command {

	private static final int[] LOOKINGORDER = {0, -1, 1, -2, 2};
	
	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		List<HorizontalProfile> result = new ArrayList<>();
		
		Trace trace = file.getTraces().get(0);
		
		
		for (int startSmp = 4; 
				startSmp < file.getMaxSamples() - 4; startSmp++) {
			
			HorizontalProfile hp = new HorizontalProfile(file.getTraces().size());
			
			if (trace.getEdge(startSmp) == 0) {
				continue;				
			}			
			
			int example = trace.getEdge(startSmp);
			int lastSmp = startSmp;
			
			int index = 0;
			int missCount = 0;
			int allMissCount = 0;
			for (Trace tr : file.getTraces()) {
				int foundSmp = findExampleAround(example, lastSmp, tr);
				if (foundSmp == -1) {
					
					hp.deep[index] = lastSmp;
					missCount++;
					allMissCount++;
				} else {
					hp.deep[index] = foundSmp;
					missCount = 0;
					
					lastSmp = foundSmp;
				}
				
				if (missCount > 6 || allMissCount > index / 3 + 10) {
					hp = null;
					break;
				}				
				index++;
			}
			if (hp != null) {
				hp.finish(file.getTraces());				
				result.add(hp);
			}
		}
		
		
		file.setProfiles(result);
	}

	public int findExampleAround(int example, int lastSmp, Trace tr) {
		
		int max = tr.numSamples() - 1;
		
		for (int ord = 0; ord < LOOKINGORDER.length; ord++) {
			int smp = lastSmp + LOOKINGORDER[ord];
			if (smp >= 0 && smp < max && tr.getEdge(smp) == example) {
				return smp;
			}
		}
		return -1;
	}

	@Override
	public String getButtonText() {
		return "Cohesive scan";
	}
}

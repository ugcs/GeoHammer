package com.ugcs.geohammer.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.element.ConstPlace;

public class ConstPointsFile {

	private List<LatLon> list;
	
	public void load(File file) {
		list = new ArrayList<>();
		
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		    	String[] dbls = line.split(" ");
		    	double d1 = Double.parseDouble(dbls[0]);
		    	double d2 = Double.parseDouble(dbls[1]);
		    	
		    	list.add(new LatLon(d1, d2));
		    }
		} catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
	public List<LatLon> getList(){
		return list;
	}
	
	public void calcVerticalCutNearestPoints(TraceFile file) {
		for(LatLon latlon : list) {
			Trace nearestTrace = null;
			double nearestTraceDistance = -1;
			
			for (Trace trace : file.getTraces()) {
				double distance = trace.getLatLon().getDistance(latlon);
				
				if (nearestTrace == null || distance < nearestTraceDistance) {
					nearestTraceDistance = distance;
					nearestTrace = trace;
				}
			}
			
			if (nearestTrace != null && nearestTraceDistance < 1.2) {
				file.getAuxElements().add(new ConstPlace(nearestTrace.getIndex(), latlon));
			}
		}
	}
}

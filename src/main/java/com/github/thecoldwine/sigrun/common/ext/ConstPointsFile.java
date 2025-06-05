package com.github.thecoldwine.sigrun.common.ext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import com.ugcs.gprvisualizer.app.auxcontrol.ConstPlace;

public class ConstPointsFile {

	private List<LatLon> list;
	
	public void load(File file) {
		list = new ArrayList<>();
		
		///
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
		    String line;
		    while ((line = br.readLine()) != null) {
		       
		    	String[] dbls = line.split(" ");
		    	double d1 = Double.parseDouble(dbls[0]);
		    	double d2 = Double.parseDouble(dbls[1]);
		    	
		    	list.add(new LatLon(d1, d2));
		    	
		    }
		}catch(Exception e) {
			e.printStackTrace();
		}		
	}
	
	public List<LatLon> getList(){
		
		return list;
	}
	
	public void calcVerticalCutNearestPoints(TraceFile fl) {
		
		for(LatLon ll : list) {
		
			Trace nearestTrace = null;			
			double nearestTraceDst = -1;
			
			for(Trace tr : fl.getTraces()) {
				
				double dst = tr.getLatLon().getDistance(ll);
				
				if(nearestTrace == null || dst < nearestTraceDst) {
					nearestTraceDst = dst;
					nearestTrace = tr;					
				}
			}
			
			if(nearestTraceDst < 1.2) {
				fl.getAuxElements().add(new ConstPlace(nearestTrace.getIndexInFile(), ll, fl.getOffset()));
			}
			
		}
		
	}
	
	
	
}

package com.ugcs.geohammer.format.kml;

import java.io.File;

import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.Model;


public class KmlSaver {

	Model model;
	
	public KmlSaver(Model model) {
		this.model = model;
	}

	public void save(File klmFile) throws Exception {
		
		KmlBuilder builder = new KmlBuilder(klmFile);
		
		int i = 1;
		for (BaseObject el : model.getAuxElements()) {
			
			if (el instanceof FoundPlace) {
				FoundPlace fp = (FoundPlace) el;
				builder.addPoint(fp.getLatLon(), "" + i);
				
				i++;
			}
		}
		
		builder.save();
	}

}

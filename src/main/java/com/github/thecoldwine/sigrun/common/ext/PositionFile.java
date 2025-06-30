package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.opencsv.CSVReader;
import com.ugcs.gprvisualizer.app.MessageBoxHelper;
import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.csv.CSVParsersFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import org.jspecify.annotations.Nullable;

public class PositionFile {

	private FileTemplates fileTemplates;

	@Nullable
	private File positionFile;

	public PositionFile(FileTemplates fileTemplates) {
		this.fileTemplates = fileTemplates;
	}

	public void load(TraceFile sgyFile) throws Exception {
		var posFile = getPositionFileBySgy(sgyFile.getFile());
		if (posFile.isPresent()) {
			load(sgyFile, posFile.get());
		} else {
			System.out.println("Position file not found for " + sgyFile.getFile().getAbsolutePath());
		}
	}

	private void load(TraceFile sgyFile, File positionFile) {
		this.positionFile = positionFile;

		String logPath = positionFile.getAbsolutePath();
		var fileTemplate = fileTemplates.findTemplate(fileTemplates.getTemplates(), logPath);

			if (fileTemplate == null) {
				throw new RuntimeException("Can`t find template for file " + positionFile.getName());
			}

			System.out.println("template: " + fileTemplate.getName());
			CsvParser parser = new CSVParsersFactory().createCSVParser(fileTemplate);

			try {				
				List<GeoCoordinates> coordinates = parser.parse(logPath);
				
				//if (sgyFile.getFile() == null) {
				//	sgyFile.setFile(csvFile);
				//}

				HorizontalProfile hp = new HorizontalProfile(sgyFile.numTraces());
   		    	StretchArray altArr = new StretchArray();

				double hair =  100 / sgyFile.getSamplesToCmAir();

				for (GeoCoordinates coord : coordinates) {
					if (coord instanceof GeoData && ((GeoData) coord).getSensorValue(GeoData.Semantic.ALTITUDE_AGL).data() != null) {
						double alt = ((GeoData) coord).getSensorValue(GeoData.Semantic.ALTITUDE_AGL).data().doubleValue();
						altArr.add((int) (alt * hair));
					}

					//if (loadAltOnly) {
					//	double hair =  100 / sgyFile.getSamplesToCmAir();
					//	altArr.add((int) (coord.getAltitude() * hair));
					//} else {
					//	sgyFile.getTraces().add(new Trace(sgyFile, null, null, new float[]{}, new LatLon(coord.getLatitude(), coord.getLongitude())));
					//	if(coord instanceof GeoData) {
					//		sgyFile.getGeoData().add((GeoData)coord);
					//	}
					//}
				}	
				
    			hp.deep = altArr.stretchToArray(sgyFile.getTraces().size());	    
   		    
		    	hp.finish(sgyFile.getTraces());			
				hp.color = Color.red;
			
				sgyFile.setGroundProfile(hp);

				sgyFile.setGroundProfileSource(this);
			
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	private Optional<File> getPositionFileBySgy(File file) {
		
		String mrkupName = null; 
		
		if (file.getName().toLowerCase().endsWith("gpr.sgy")) {
			mrkupName = StringUtils.replaceIgnoreCase(
					file.getAbsolutePath(), "gpr.sgy", "position.csv");
		} else if (file.getName().toLowerCase().endsWith(".dzt")) {
			mrkupName = StringUtils.replaceIgnoreCase(
					file.getAbsolutePath(), ".dzt", ".mrkup");
		}
				
		return mrkupName != null ? Optional.of(new File(mrkupName)) : Optional.empty();
	}

	@Nullable
	public File getPositionFile() {
		return positionFile;
	}
}

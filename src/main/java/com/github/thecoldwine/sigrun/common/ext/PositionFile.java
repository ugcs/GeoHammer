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

	public void load(SgyFile sgyFile) throws Exception {
		var posFile = getPositionFileBySgy(sgyFile.getFile());
		if (posFile.isPresent()) {
			load(sgyFile, posFile.get());
		} else {
			System.out.println("Position file not found for " + sgyFile.getFile().getAbsolutePath());
		}
	}

	private void load(SgyFile sgyFile, File positionFile) {
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

				HorizontalProfile hp = new HorizontalProfile(sgyFile.getTraces().size());		    
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


	public void load_old(SgyFile sgyFile, File posfile) throws Exception {

		if (!posfile.exists()) {
			System.out.println("Position file not exists " + posfile.getAbsolutePath());
			return;
		}
		
		//[Elapsed, Date, Time, Pitch, Roll, Yaw, Latitude, Longitude, Altitude, Velocity, RTK Status, Latitude RTK, Longitude RTK, Altitude RTK, ALT:Altitude, ALT:Filtered Altitude, GPR:Trace]
		//[309793, 2021/05/12, 07:48:58.574, -6.03, -0.73, 137.52, 56.86301828, 24.11194153, 3.60, 5.20, OFF, , , , 2.91, 2.91, 999]

		//Elapsed, Date, Time, Pitch, Roll, Heading, Latitude, Longitude, Altitude, Next WP, Velocity, Status RTK, Latitude RTK, Longitude RTK, Altitude RTK, GPR:Trace

		try (CSVReader csvReader = new CSVReader(new FileReader(posfile));) {
			
			String[] header = csvReader.readNext();
			
			//точная высота относительно земли высотометр
			int altAltIndex = ArrayUtils.indexOf(header, "ALT:Altitude");
			
			//барометрическая относительно точки взлета
			int altIndex = ArrayUtils.indexOf(header, "Altitude");
			
			//эллипсоидная относительно уровня моря
			int altRtkIndex = ArrayUtils.indexOf(header, "Altitude RTK");

			//колонка "номер трейса". может называться по разному
			int gprTrace = IntStream.range(0, header.length)
				.filter(i -> StringUtils.containsIgnoreCase(header[i], "GPR:Trace"))
				.findFirst().getAsInt();

		    String[] values = null;
		    
		    HorizontalProfile hp = new HorizontalProfile(sgyFile.getTraces().size());
		    HorizontalProfile hp2 = new HorizontalProfile(sgyFile.getTraces().size());
		    
		    double hair =  100 / sgyFile.getSamplesToCmAir();
		    
   		    StretchArray altArr = new StretchArray();
   		    StretchArray altAltArr = new StretchArray();
   		    while ((values = csvReader.readNext()) != null) {
 	
		    	
	    	    //skip empty row or traces less than positions
		    	if(values.length >= 3 && StringUtils.isNotBlank(values[gprTrace])) {
//		    		hp.deep[posCount] = (int)(Double.valueOf(values[altAltIndex]) * hair);
//		    		hp2.deep[posCount] = (int)(Double.valueOf(values[altIndex]) * hair);
					if (altAltIndex != -1 && StringUtils.isNotBlank(values[altAltIndex])) {
						altAltArr.add((int) (Double.valueOf(values[altAltIndex]) * hair));
					}

					if (altIndex != -1 && StringUtils.isNotBlank(values[altIndex])) {
						altArr.add((int) (Double.valueOf(values[altIndex]) * hair));
					}
				}
			}

    		hp.deep = altAltArr.stretchToArray(sgyFile.getTraces().size());
    		hp2.deep = altArr.stretchToArray(sgyFile.getTraces().size());
   		    
   		    
		    hp.finish(sgyFile.getTraces());			
			hp.color = Color.red;
			
			sgyFile.setGroundProfile(hp);
			
			
			hp2.finish(sgyFile.getTraces());			
			hp2.color = Color.green;
			
			System.out.println("Count of traces in position file is: " + altAltArr.size() + ",  Count of traces in GPR file is: " + sgyFile.getTraces().size());
			if (altAltArr.size() != sgyFile.getTraces().size()) {
				
				MessageBoxHelper.showError(
						"Warning", 
						"Count of traces in GPR file is " + sgyFile.getTraces().size() 
						+ " and count of traces in position file is " + altAltArr.size()
						+ ". \nPositions array is stretched to fit trace count.");
			}
			
			sgyFile.profiles = new ArrayList<>();
			sgyFile.profiles.add(hp2);
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

package com.ugcs.geohammer.format.kml;

import java.io.File;
import java.io.FileOutputStream;

import com.ugcs.geohammer.model.LatLon;

import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Folder;
import de.micromata.opengis.kml.v_2_2_0.Icon;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.Style;
import de.micromata.opengis.kml.v_2_2_0.Units;
import de.micromata.opengis.kml.v_2_2_0.Vec2;

public class KmlBuilder {

	final Kml kml = new Kml();
	Document doc;
	Folder folder; 
	File file;
	
	public KmlBuilder(File file) {
		this.file = file;
		
		doc = kml.createAndSetDocument()
				.withName(file.getName())
				.withOpen(true);
		
		initStyles();			
		
		// create a Folder
		folder = doc.createAndAddFolder();
		folder.withName(file.getName())
			.withOpen(true);
		
	}

	public void initStyles() {
		Icon icon = new Icon().withHref("http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png");
		
		Style style = doc.createAndAddStyle();
		
		Vec2 v = new Vec2();
		
		//<hotSpot x="20" y="2" xunits="pixels" yunits="pixels"/>
		v.setX(20);
		v.setY(2);
		v.setXunits(Units.PIXELS);
		v.setYunits(Units.PIXELS);
		
		style.withId("style_push") 
		    .createAndSetIconStyle()
		    .withScale(1)
		    .withIcon(icon)
		    .withHotSpot(v); 
		
		//style.createAndSetLabelStyle()
		//	.withColor("ff43b3ff");
	}
	
	public void addPoint(LatLon ll, String name) {
		Placemark placemark = folder.createAndAddPlacemark();

		placemark.setStyleUrl("#style_push");
		
		// use the style for each continent
		placemark.withName(name)
		    // coordinates and distance (zoom level) of the viewer
		    .createAndSetLookAt()
		    .withLongitude(ll.getLonDgr())
		    .withLatitude(ll.getLatDgr())
		    .withAltitude(0)
		    .withRange(1200);
		
		placemark.createAndSetPoint()
			.addToCoordinates(ll.getLonDgr(), ll.getLatDgr());
		
	}
	
	public void save() throws Exception {
		
		FileOutputStream os = new FileOutputStream(file);
		
		kml.marshal(os);
		
		os.close();
		
		
	}
	
	private static void createPlacemarkWithChart(Document document, 
			Folder folder, 
			double longitude, double latitude, 
			String name) {

		Placemark placemark = folder.createAndAddPlacemark();

		placemark.setStyleUrl("#style_push");
		
		// use the style for each continent
		placemark.withName(name)
		    // coordinates and distance (zoom level) of the viewer
		    .createAndSetLookAt()
		    .withLongitude(longitude)
		    .withLatitude(latitude)
		    .withAltitude(0)
		    .withRange(200)
		    .withTilt(0);
		
		placemark.createAndSetPoint().addToCoordinates(longitude, latitude);
		
		
	}

	public static void main(String[] args) throws Exception {
		
		
		final Kml kml = new Kml();
		Document doc = kml.createAndSetDocument()
				.withName("JAK Example1").withOpen(true);

		Icon icon = new Icon().withHref("http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png");
		Style style = doc.createAndAddStyle();
		
		style.withId("style_push") 
		    .createAndSetIconStyle()
		    .withIcon(icon);

		style.createAndSetLabelStyle()
			.withColor("ff43b3ff");			
		
		// create a Folder
		Folder folder = doc.createAndAddFolder();
		folder.withName("Continents with Earth's surface").withOpen(true);

		
		
		createPlacemarkWithChart(doc, folder, 93.24607775062842, 47.49808862281773, "Asia");

		// print and save
		kml.marshal(new File("d:/tmp/tiff/advancedexample1.kml"));
		
	}
	
	
}

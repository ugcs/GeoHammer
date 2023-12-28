package com.ugcs.gprvisualizer.draw;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import javax.imageio.ImageIO;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;

public class HereMapProvider implements MapProvider {

	public int getMaxZoom() {
		return 20;
	}
	
	@Override
	public BufferedImage loadimg(MapField field) {
		System.out.println(field.getZoom());
		if (field.getZoom() > getMaxZoom()) {
			field.setZoom(getMaxZoom());
		}
		
		BufferedImage img = null;
		
		
		LatLon midlPoint = field.getSceneCenter();
		int imgZoom = field.getZoom();
		//map.setLocation(new Location(midlPoint.getLatDgr(), midlPoint.getLonDgr()), imgZoom); 
		
		 //DecimalFormat df = new DecimalFormat("#.000000");
		DecimalFormat df = new DecimalFormat("#.0000000", DecimalFormatSymbols.getInstance(Locale.US));
		
		try {
			String HERE_API_KEY = "mX93tKDhNQW4jB9qWR7U8njVda4OWZu9S8t7Q1blkCs";
			String url = String.format("https://image.maps.ls.hereapi.com/mia/1.6/mapview?"
					+ "apiKey=%s"
					+ "&c=%s,%s"
					+ "&t=3"
					+ "&z=%d"
					+ "&nodot&w=1200&h=1200", 
					HERE_API_KEY, 
					df.format(midlPoint.getLatDgr()), 
					df.format(midlPoint.getLonDgr()), 
					imgZoom);
			
			System.out.println(url);
			
			System.setProperty("java.net.useSystemProxies", "true");
			img = ImageIO.read(new URI(url).toURL());
			
		} catch (IOException | URISyntaxException e) {
			System.err.println(e.getMessage());
		}
		
		return img;
	}

}

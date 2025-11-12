package com.ugcs.geohammer;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class Settings {

	public enum RadarMapMode {
		AMPLITUDE,
		SEARCH
	}
	
	public RadarMapMode radarMapMode = RadarMapMode.AMPLITUDE;
	
	public boolean isRadarMapVisible = true;
	
	public int maxsamples = 400;
	
	public int width = 800;
	public int height = 600;
	public int radius = 15;
	
	public int hpage = 47;

	private int layer = 80; 
	
	public int hypermiddleamp = 0;
	
	public MutableBoolean showEdge = new MutableBoolean(); 
	public MutableBoolean showGood = new MutableBoolean();
	public MutableBoolean hyperliveview = new MutableBoolean();
	public MutableInt levelPreviewShift = new MutableInt(0);
	
	public int topscale = 200;
	public int bottomscale = 250;
	public int zoom = 100;
    
	public boolean autogain = true;
	
	public int threshold = 0;
    
	public int getWidth() {
		return (int) (width * zoom / 100.0);
	}
	
	public int getHeight() {
		return (int) (height * zoom / 100.0);
	}
	
	public MutableBoolean getHyperliveview() {
		return hyperliveview;
	}

	public int getLayer() {
		return layer;
	}

	public void setLayer(int layer) {
		this.layer = layer;
	}	
}

package com.ugcs.gprvisualizer.gpr;

import java.awt.geom.Point2D;
import java.io.File;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class Settings {

	public enum RadarMapMode {
		AMPLITUDE,
		SEARCH
	}
	
	public RadarMapMode radarMapMode = RadarMapMode.AMPLITUDE;
	
	//
	
	public boolean showGreenLine = true;

	public boolean isRadarMapVisible = true;
	
	public int maxsamples = 400;
	
	public int width = 800;
	public int height = 600;
	public int radius = 15;
	
	public int hpage = 47;

	private int layer = 80; 
	
	
	public int hyperkfc = 25; 
	public MutableInt hyperSensitivity = new MutableInt(30);
	public MutableInt printHoughAindex = new MutableInt(0);
	public MutableInt printHoughVertShift = new MutableInt(0);
	
	public int hypermiddleamp = 0;
	
	public MutableBoolean showEdge = new MutableBoolean(); 
	public MutableBoolean showGood = new MutableBoolean();
	public MutableBoolean hyperliveview = new MutableBoolean();
	public MutableBoolean levelPreview = new MutableBoolean(false);
	public MutableInt levelPreviewShift = new MutableInt(0);
	
	public int topscale = 200;
	public int bottomscale = 250;
	public int cutscale = 100;
	public int zoom = 100;
    
	public boolean showpath = true;
	public boolean autogain = true;
	
	public int threshold = 0;
    
	public int distBetweenTraces = 10;
	
	//TODO: unused
	public int selectedScanIndex = 1;
	public double kf;
	public double stx;
	public double sty;
	
	
	public int widthZoomKf = 30;
	public int heightZoomKf = 100;
	public int heightStart = 0;

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

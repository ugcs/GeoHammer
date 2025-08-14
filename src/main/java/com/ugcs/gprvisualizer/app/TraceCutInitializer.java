package com.ugcs.gprvisualizer.app;

import java.util.ArrayList;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Nulls;
import javafx.geometry.Point2D;

public class TraceCutInitializer {

	public List<LatLon> initialRect(Model model) {
		MapField f = new MapField(model.getMapField());
		f.setZoom(30);
		
		List<Point2D> scrpos = new ArrayList<>();
		double sx = 0;
		double sy = 0;
		for (SgyFile file : model.getFileManager().getFiles()) {
			for (GeoData value : Nulls.toEmpty(file.getGeoData())) {
				LatLon latlon = value.getLatLon();
				if (latlon == null) {
					continue;
				}
				Point2D p = f.latLonToScreen(latlon);
				sx += p.getX();
				sy += p.getY();
				scrpos.add(p);
			}
		}

		Point2D center = !scrpos.isEmpty()
				? new Point2D(sx / scrpos.size(), sy / scrpos.size())
				: new Point2D(0, 0);

		double maxrad = 0;
		//double
		int[] angcount = new int[180];
		for (int i = 50; i < scrpos.size(); i++) {
			Point2D p1 = scrpos.get(i - 50);
			Point2D p2 = scrpos.get(i);
			
			double ang = Math.atan2(p1.getY() - p2.getY(), p1.getX() - p2.getX());
			int angdgr =  (int) (ang * 180 / Math.PI);
  
			for (int j = -9; j <= 9; j++) {
				angcount[(j + angdgr + 360) % 180]++;
			}
			
			
			maxrad = Math.max(maxrad, center.distance(p1));
		}
		int maxindex = 0;
		for (int i = 0; i < 180; i++) {			
			if (angcount[maxindex] < angcount[i]) {
				maxindex = i;
			}
		}
		
		System.out.println(" best degree " + maxindex);
		
		double ang = ((double) maxindex + 45) * Math.PI / 180.0; 
		
		List<LatLon> points = new ArrayList<>();
		
		points.add(f.screenTolatLon(new Point2D(
				Math.cos(ang) * maxrad + center.getX(),
				Math.sin(ang) * maxrad + center.getY())));
		
		ang += Math.PI / 2;
		
		points.add(f.screenTolatLon(new Point2D(
				Math.cos(ang) * maxrad + center.getX(),
				Math.sin(ang) * maxrad + center.getY())));
		
		ang += Math.PI / 2;
		
		points.add(f.screenTolatLon(new Point2D(
				Math.cos(ang) * maxrad + center.getX(),
				Math.sin(ang) * maxrad + center.getY())));
		
		ang += Math.PI / 2;
				
		points.add(f.screenTolatLon(new Point2D(
				Math.cos(ang) * maxrad + center.getX(),
				Math.sin(ang) * maxrad + center.getY())));
				
		points.add(1, points.get(0).midpoint(points.get(1)));
		points.add(3, points.get(2).midpoint(points.get(3)));
		points.add(5, points.get(4).midpoint(points.get(5)));
		points.add(7, points.get(6).midpoint(points.get(0)));
		
		return points;
	}
}

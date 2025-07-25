package com.ugcs.gprvisualizer.gpr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.GPRChart;
import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;
import org.apache.commons.lang3.tuple.Pair;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObjectImpl;

public class LeftRulerController {
	public static Stroke STROKE = new BasicStroke(1.0f);
	
	public interface Converter {
		Pair<Integer, Integer> convert(int s, int f);
		
		int back(int unt);
		
		String getUnit();
	}
	

	private final Converter[] list;
	private int index = 0;
	
	public LeftRulerController(ProfileField profileField) {
		list = new Converter[] {
                new SamplConverter(), new NanosecConverter(profileField.getFile())
        };
	}
	
	public Converter getConverter() {
		return list[index];
	}
	
	public void nextConverter() {
		index = (index + 1) % list.length;
	}
	
	
	static class SamplConverter implements Converter {

		@Override
		public Pair<Integer, Integer> convert(int s, int f) {
			return Pair.of(s, f);
		}
		
		public int back(int unt) {
			return unt;
		}

		@Override
		public String getUnit() {
			return "smpl";
		}		
	}

	static class NanosecConverter implements Converter {

		private final TraceFile sgyFile;

		public NanosecConverter(TraceFile sgyFile) {
			this.sgyFile = sgyFile;
		}

		@Override
		public Pair<Integer, Integer> convert(int s, int f) {
			int sampleInterval = sgyFile.getSampleInterval();

			return Pair.of(
					(int)Math.ceil(sampleInterval * s / 1000.0),
					sampleInterval * f / 1000);
		}
		
		public int back(int unt) {
			TraceFile fl = sgyFile;
			return unt * 1000 / fl.getSampleInterval();
		}

		@Override
		public String getUnit() {
			return "  ns";
		}		
	}
	
	public BaseObject getTB() {
		return tb;
	}

	private final BaseObject tb = new BaseObjectImpl() {

		@Override
		public void drawOnCut(Graphics2D g2, ScrollableData scrollableData) {
			if (scrollableData instanceof GPRChart gprChart) {
				//setClip(g2, profField.getInfoRect());
				g2.setClip(null);
				Rectangle r = getRect(gprChart.getField());

				g2.setStroke(STROKE);
				g2.setColor(Color.lightGray);
				g2.drawRoundRect(r.x, r.y, r.width, r.height, 7, 7);

				g2.setColor(Color.darkGray);
				String text = getConverter().getUnit();
				int width = g2.getFontMetrics().stringWidth(text);
				g2.drawString(text, r.x + r.width - width - 6, r.y + r.height - 7);
			}
		}

		//@Override
		private Rectangle getRect(ProfileField profField) {
				Rectangle  r = profField.getInfoRect();
				return new Rectangle(profField.getVisibleStart() + r.x + 12, r.y + r.height - 35,
						r.width - 15, 20);
		}

		@Override
		public boolean isPointInside(Point2D localPoint, ScrollableData scrollableData) {
			if (scrollableData instanceof GPRChart gprChart) {
				Rectangle rect = getRect(gprChart.getField());
				return rect.contains(localPoint.getX(), localPoint.getY());
			}
			return false;
		}

		@Override
		public boolean mousePressHandle(Point2D localPoint, ScrollableData scrollableData) {
			if (isPointInside(localPoint, scrollableData)) {
				nextConverter();
				return true;
			}
			return false;
		}
	};

}

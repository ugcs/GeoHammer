package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceSample;
import org.jspecify.annotations.Nullable;

public class RulerTool extends BaseObjectImpl {
	
	private static final Font fontB = new Font("Verdana", Font.BOLD, 11);
	private static final double MARGIN = 1.0;
		
	private final DragAnchor anch1;
	private final DragAnchor anch2;
	private final TraceFile file;
	
	private static class RulerAnchor extends DragAnchor {
		
		public RulerAnchor(Image img, AlignRect alignRect) {
			super(img, alignRect);
		}

		@Override
		protected void realDraw(Graphics2D g2, Rectangle rect) {
		
			g2.setColor(Color.BLACK);
			g2.fillOval(rect.x, rect.y, rect.width, rect.height);

			g2.setColor(Color.YELLOW);
			g2.drawOval(rect.x, rect.y, rect.width, rect.height);
		
		}
	}
	
	public RulerTool(TraceFile file, int s, int f, int smpStart, int smpFinish) {
		this.file = file;

		anch1 = new RulerAnchor(ResourceImageHolder.IMG_VER_SLIDER, AlignRect.CENTER) {

			@Override
			public void signal(@Nullable Object obj) {
				anch1.setTrace(
					Math.clamp(anch1.getTrace(), 0, file.numTraces() - 1));
				anch1.setSample(
						Math.clamp(anch1.getSample(),
								0, file.getMaxSamples()));

			}
		};
		
		anch2 = new RulerAnchor(ResourceImageHolder.IMG_VER_SLIDER, AlignRect.CENTER) {

			@Override
			public void signal(@Nullable Object obj) {
				anch2.setTrace(
						Math.clamp(anch2.getTrace(),
								0, file.numTraces() - 1));
				anch2.setSample(
						Math.clamp(anch2.getSample(),
								0, file.getMaxSamples()));
			}
		};		
		
		anch1.setTrace(s);
		anch1.setSample(smpStart);
		anch2.setTrace(f);
		anch2.setSample(smpFinish);
	}
	
	@Override
	public void drawOnCut(Graphics2D g2, ScrollableData profField) {
		Point2D lt = profField.traceSampleToScreen(new TraceSample(
				anch1.getTrace(), anch1.getSample()));
		Point2D rb = profField.traceSampleToScreen(new TraceSample(
				anch2.getTrace(), anch2.getSample()));
		
		g2.setColor(Color.RED);
		g2.drawLine((int) lt.getX(), (int) lt.getY(), (int) rb.getX(), (int) rb.getY());
		
		g2.drawLine((int) lt.getX(), (int) lt.getY(), (int) lt.getX(), (int) rb.getY());
		
		g2.drawLine((int) lt.getX(), (int) rb.getY(), (int) rb.getX(), (int) rb.getY());
		
		g2.setColor(Color.GRAY);
		
		g2.setFont(fontB);
		int fontHeight = g2.getFontMetrics().getHeight();

		String smpDsp = Math.abs(anch2.getSample() - anch1.getSample()) + " smp";
		
		String trcDsp = Math.abs(anch2.getTrace() - anch1.getTrace()) + " tr";
		
		String distDsp = String.format("%.2f cm", dist());
		
		drawText(g2, lt.getX() + 3, (lt.getY() + rb.getY()) / 2, smpDsp);
		
		drawText(g2, (lt.getX() + rb.getX()) / 2, rb.getY() - 3, trcDsp);
		
		drawText(g2, (lt.getX() + rb.getX()) / 2 + 5 * fontHeight,
				(lt.getY() + rb.getY()) / 2 + 2 * fontHeight, distDsp);
	}
	
	private void drawText(Graphics2D g2, double x, double y, String str) {
        FontMetrics fm = g2.getFontMetrics();
        Rectangle2D rect = fm.getStringBounds(str, g2);

        rect = new Rectangle2D.Double(rect.getX() - MARGIN - 2, 
        		rect.getY() - MARGIN, 
        		rect.getWidth() + 2 * MARGIN + 3, 
        		rect.getHeight() + 2 * MARGIN);
        
        g2.setColor(Color.BLACK);
        
        g2.fillRoundRect((int) (x + rect.getX()),
				(int) (y + rect.getY()),
				(int) rect.getWidth(),
				(int) rect.getHeight(),
				5, 5);
        
        g2.setColor(Color.YELLOW.darker());
        g2.drawRoundRect((int) (x + rect.getX()),
				(int) (y + rect.getY()),
				(int) rect.getWidth(),
				(int) rect.getHeight(),
				5, 5);

        g2.setColor(Color.MAGENTA);
        g2.drawString(str, (int) x, (int) y);
	}
	
	private double dist() {
		int tr1 = anch1.getTrace();
		int tr2 = anch2.getTrace();
		int smp1 = anch1.getSample();
		int smp2 = anch2.getSample();
		
		double diag = distanceCm(file, tr1, tr2, smp1, smp2);
		
		return diag;
	}

	public static double distanceVCm(TraceFile file, int tr, double smp1, double smp2) {
		double grndLevel = 0;
		if (file.getGroundProfile() != null) {
			grndLevel = file.getGroundProfile().getDepth(tr);
		}

		double h1 = Math.min(smp1, smp2); 
		double h2 = Math.max(smp1, smp2);
		
		double hair = Math.max(0,  Math.min(grndLevel, h2) - h1); 
		double hgrn = h2 - h1 - hair;
		
		double vertDistCm =  file.getSamplesToCmAir() * hair 
				+ file.getSamplesToCmGrn() * hgrn;

		return vertDistCm;
	}
	
	public static double distanceCm(TraceFile file, int tr1, int tr2, double smp1, double smp2) {
		double grndLevel = 0;
		if (file.getGroundProfile() != null) {
			grndLevel = file.getGroundProfile().getDepth((tr1 + tr2) / 2);
		}

		int s = Math.max(0, Math.min(tr1, tr2));
		int f = Math.min(file.numTraces() - 1, Math.max(tr1, tr2));
		
		List<Trace> traces = file.getTraces();
		
		double dst = 0;
		for (int i = s + 1; i <= f; i++) {
			dst += traces.get(i).getPrevDist();
		}
		
		double horDistCm = dst;		
		
		double h1 = Math.min(smp1, smp2); 
		double h2 = Math.max(smp1, smp2);
		
		double hair = Math.max(0,  Math.min(grndLevel, h2) - h1); 
		double hgrn = h2 - h1 - hair;
		
		double vertDistCm =  file.getSamplesToCmAir() * hair 
				+ file.getSamplesToCmGrn() * hgrn;
		
		double diag = Math.sqrt(horDistCm * horDistCm + vertDistCm * vertDistCm);
		return diag;
	}

	@Override
	public List<BaseObject> getControls() {
		return List.of(anch1, anch2);
	}

	@Override
	public boolean isFit(int begin, int end) {
		return true;
	}
}

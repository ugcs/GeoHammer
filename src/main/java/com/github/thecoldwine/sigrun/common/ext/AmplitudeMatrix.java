package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.tuple.Pair;

import com.ugcs.gprvisualizer.gpr.PaletteBuilder;
import com.ugcs.gprvisualizer.math.BackgroundRemovalFilter;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.math.MovingAvg;

public class AmplitudeMatrix {

	float[][] matrix;
	float maxamp;
	List<Trace> traces;

	List<Grp> avgrow = new ArrayList<>();
	List<List<Grp>> colls = new ArrayList<>();
	List<List<Grp>> selected = new ArrayList<>();
	List<List<Grp>> foundgroups = new ArrayList<>();
	MovingAvg startAvg = new MovingAvg();
	MovingAvg finishAvg = new MovingAvg();
	List<List<Integer>> rows;
	//int level[];
	
	public void init(List<Trace> traces) {
		
		avgrow = new ArrayList<>();
		colls = new ArrayList<>();
		selected = new ArrayList<>();
		foundgroups = new ArrayList<>();
		startAvg = new MovingAvg();
		finishAvg = new MovingAvg();
		//level = null;
		
		
		this.traces = traces;
		matrix = new float[traces.size()][];
		for (int i = 0; i < traces.size(); i++) {
			matrix[i] = getAmpVector(traces.get(i).getNormValues());
		}		
	}

	private float[] getAmpVector(float[] values) {
		
		float[] res = new float[values.length];
		colls.add(new ArrayList<>());
		float prevval = 0;
		float max = values[0];
		float min = values[0];
		int zeroind = 0;
		int midind = 0;
		for (int i = 0; i < values.length; i++) {
			
			float val = values[i];
			max = Math.max(max, val);
			min = Math.min(min, val);
			
			if (isCrosZero(val, prevval)) {
				midind = i;
			}
			if (isCrosZero(prevval, val)) {
				//float amp = ;
				
				fill(res, zeroind, i, max - min);
				colls.get(colls.size() - 1).add(
						new Grp(zeroind, i, max - min, midind));
				zeroind = i;
				min = val;
				max = val;
			}
			prevval = val;
		}
		fill(res, zeroind, values.length, max - min);
		return res;
	}
	
	class Grp{
		int start;
		int midind;
		int finish;
		float amp;

		public Grp(int start, int finish, float f, int midind) {
			this.start = start;
			this.finish = finish;
			this.amp = f;
			this.midind =  midind;
		}
	}
	
	/**
	 * * * * * * * * * * * * * * * * * * *
	 */
	public HorizontalProfile findLevel() {
		//first col ordered by amp
		
		List<Grp> startList = getOrderedDescGrps();
		List<List<Grp>> selected = new ArrayList<>();
		
		//select[X] - cohesive union of groups (row)
		for (Grp start : startList) {
			selected.add(findLevelStartingAt(start));
		}
		
		// integer arrays - (top or bottom) border line of band
		rows = getRows(selected);
		
		List<Integer> selrow = findBestPath(rows);
		
		
		this.selected.addAll(selected);
		
		
		return prepareLevel(selrow);
	}

	private HorizontalProfile prepareLevel(List<Integer> selrow) {
		
		HorizontalProfile hp = new HorizontalProfile(selrow.size());
		
		//level = new int[selrow.size()];
		
		for (int i = 0; i < selrow.size(); i++) {
			 int r = calcAvgHeight(selrow, i);
			 
			 hp.deep[i] = r;
			 //level[i] = r;
			 //trace.get(i).maxindex = r;
			 //trace.get(i).maxindex2 = r;
		}
		
		hp.finish(traces);
		
		hp.color = Color.red;
		
		return hp;
	}

	private List<List<Integer>> getRows(List<List<Grp>> selected) {
		List<List<Integer>> rows = new ArrayList<>();
		for (List<Grp> row : selected) {
			List<Integer> lstop = new ArrayList<>();
			List<Integer> lsbot = new ArrayList<>();
			List<Integer> lscen = new ArrayList<>();
			for (Grp g : row ) {
				lstop.add(g.start);
				lscen.add(g.midind);
				lsbot.add(g.finish - 1);
			}
			rows.add(lstop);
			//rows.add(lscen);
			rows.add(lsbot);
		}
		return rows;
	}

	private static final int R = 20;
	
	private int calcAvgHeight(List<Integer> selrow, int i) {
		
		int from = i - R;
		from = Math.max(from, 0);
		
		int to = i + R;
		to = Math.min(to, selrow.size() - 1);

		double sum = 0;
		double del = 0;
		for (int j = from; j <= to; j++) {
			
			double d = ((double) (R - Math.abs(j - i))) 
					/ ((double) R);
			
			sum += selrow.get(j) * d;
			del += d;
		}
		
		return (int) Math.round(sum / del);
	}

	private List<Integer> findBestPath(List<List<Integer>> selected) {
		List<Integer> selrow = null;
		long selsum = 0;
		for (List<Integer> row : selected) {
			long sum = getIndignation(row);
			
			if (selrow == null || sum < selsum) {
				selrow = row;
				selsum = sum;
			}			
		}
		return selrow;
	}
	
	private long getIndignation(List<Integer> row) {
		long res = 0;
		
		// continuos number of zero shift
		int zerocount = 0;
		int maxzerocount = 0;
		for (int i = 1; i < row.size(); i++) {
			
			int diff = Math.abs(row.get(i - 1) - row.get(i));
			res += diff;
			if (diff == 0) {
				zerocount++;
			} else {
				maxzerocount = Math.max(maxzerocount, zerocount);
				zerocount = 0;
			}
		}
		maxzerocount = Math.max(maxzerocount, zerocount);
		
		if (maxzerocount > row.size() / 4) {
			//it s strait line - can`t be the ground.  
			//Most probable it is background const noise
			return 10000000;
		}
		return res;
	}

	private List<Grp> getOrderedDescGrps() {
		List<Grp> r = new ArrayList<Grp>(colls.get(0));
		Collections.sort(r, new Comparator<Grp>() {

			@Override
			public int compare(Grp o1, Grp o2) {
				
				return -Float.compare(o1.amp, o2.amp);
			}
		});
		
		
		//return r.subList(0, r.size());
		return r.subList(0, Math.min(6, r.size()));
	}

	List<Grp> findLevelStartingAt(Grp start) {
		
		List<Grp> selected = new ArrayList<>();
		selected.add(start);
		startAvg = new MovingAvg();
		startAvg.add(start.start);
		finishAvg = new MovingAvg();
		finishAvg.add(start.finish);
		
		int x = 0;
		for (List<Grp> col : colls) {
			
			Grp avggrp = getAvgGrp();
			avgrow.add(avggrp);
			List<Grp> grpcandidats = find(avggrp, col);
			if (grpcandidats.isEmpty()) {
				
				selected.add(selected.get(selected.size() - 1));
			} else {
			
				Grp grp = selectbest(grpcandidats, selected);
				startAvg.add(grp.start);
				finishAvg.add(grp.finish);
				selected.add(grp);
			}
			
			x++;
		}
		selected.remove(0);
		return selected;
	}

	private Grp selectbest(List<Grp> grpcandidats, List<Grp> selected) {
//		Grp last = selected.get(selected.size()-1);
//		
//		Grp r1 = findBestByAmp(grpcandidats);
//		
//		Grp r2 = findBestByCommon(grpcandidats, last);
//		
//		if(r1 == r2) {
//			return r1;
//		}
		Grp avggrp = getAvgGrp();
		
	
		return findBestByAmp(findBestByCommonLst(grpcandidats, avggrp));
	}

	private Grp getAvgGrp() {
		Grp avggrp = new Grp(startAvg.get(), finishAvg.get(), 
				2000, (startAvg.get() + finishAvg.get()) / 2);
		return avggrp;
	}

	private List<Grp> findBestByCommonLst(List<Grp> grpcandidats, Grp last) {
		List<Pair<Integer, Grp>> lst = new ArrayList<>();
		for (Grp grp : grpcandidats) {
			
			lst.add(Pair.of(com(grp, last), grp));
		}
		
		Collections.sort(lst, new Comparator<Pair<Integer, Grp>>(){

			@Override
			public int compare(Pair<Integer, Grp> arg0, Pair<Integer, Grp> arg1) {
				
				return -Integer.compare(arg0.getKey(), arg1.getKey());
			}			
		});
		
		List<Grp> res = new ArrayList<>();
		res.add(lst.get(0).getValue());
		if (lst.size() > 1 
			&& diffMinor(lst.get(0).getKey(), lst.get(1).getKey())) {
			
			res.add(lst.get(1).getValue());
		}
		return res;
	}
	
	private boolean diffMinor(Integer k1, Integer k2) {
		int thr = Math.max(k1, k2) / 3;
		return Math.abs(k1 - k2) <= thr;
		
	}

	private Grp findBestByCommon(List<Grp> grpcandidats, Grp last) {
		Grp r2 = grpcandidats.get(0);
		for (Grp grp : grpcandidats) {
			
			if (com(grp, last) > com(r2, last)) {
				r2 = grp;
			}
		}
		return r2;
	}

	private Grp findBestByAmp(List<Grp> grpcandidats) {
		Grp r1 = grpcandidats.get(0);
		for (Grp grp : grpcandidats) {
			
			
			if (grp.amp > r1.amp) {
				r1 = grp;
			}
		}
		return r1;
	}

	private int com(Grp g1, Grp g2) {
		
		return 
			Math.min(g1.finish, g2.finish) 
			- Math.max(g1.start, g2.start);
	}

	private List<Grp> find(Grp last, List<Grp> col) {
		List<Grp> result = new ArrayList<>();
		
		for (Grp grp : col) {
			if (isTouched(last, grp)) {
				result.add(grp);
			}
		}
		
		
		return result;
	}

	private boolean isTouched(Grp last, Grp grp) {

		return last.start >= grp.start && last.start <= grp.finish
				||
			   last.finish >= grp.start && last.finish <= grp.finish
			    ||
			   grp.start >= last.start && grp.start <= last.finish
				||
			   grp.finish >= last.start && grp.finish <= last.finish;
	}

	private void fill(float[] res, int zeroind, int to, float amp) {

		for (int i = zeroind; i < to; i++) {
			res[i] = amp; 
		}
		
		maxamp = Math.max(maxamp, amp);
	}

	private boolean isCrosZero(float prevval, float val) {
		
		return prevval < 0 && val >= 0;
	}

	int vertkf = 4;
	int width;
	int height;
	
	public BufferedImage getImg() {
		
		int[] palette = new PaletteBuilder().build();
		width = matrix.length;
		height = matrix[0].length;
	    BufferedImage image = new BufferedImage(width, height * vertkf, 
	    		BufferedImage.TYPE_INT_RGB);
	    
	    int[] buffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData() ;	    
	    
	    for (int x = 0; x < width; x++) {
	    	for (int y = 0; y < height; y++) {
	    	
	    		int  s = (int) (matrix[x][y] / 30);
	    		s = Math.min(s, 2000);
	    		
	    		for (int hy = 0; hy < vertkf; hy++) {
	    			buffer[getIndex(x, y, hy)] = palette[s];
	    		}
	    	
	    	}
	    }
	    
	    int grn = Color.GREEN.getRGB();
	    int grn2 = (new Color(0, 200, 50)).getRGB();
	    
	    int red = Color.RED.getRGB();
	    int levelColor = Color.BLUE.getRGB();
	    
	    if (rows != null) {
		    for (List<Integer> row : rows) {
		    	int x = 0;
		    	for (Integer col : row) {
		    		buffer[getIndex(x, col, 2)] = grn;
		    		x++;
		    	}
		    	
		    }
	    }
    	
//	    if(level != null) {
//	    	for(int x=0; x <level.length; x++) {
//	    		buffer[getIndex(x, level[x], 1)] = red;    		
//	    		buffer[getIndex(x, level[x], 3)] = red;
//	    	}
//	    }
	    
	    return image;
		
	}

	private int getIndex(int x, int y, int hy) {
		return x + y * vertkf * width + hy * width;
	} 
	
	public static void execute(File file, int prefix) throws Exception {
		
		TraceFile sgyFile = new GprFile();
		sgyFile.open(file);
		
		BackgroundRemovalFilter lf = new BackgroundRemovalFilter();
		lf.removeConstantNoise(sgyFile.getTraces());
		
		AmplitudeMatrix m = new AmplitudeMatrix();
		m.init(sgyFile.getTraces());
		
		m.findLevel();
		
		BufferedImage img = m.getImg();
		
		String name = String.format("%02d_image.png", prefix);
		File outputfile = new File(name);
		ImageIO.write(img, "png", outputfile);
		
		 Desktop.getDesktop().open(outputfile);
	}

	
	public BufferedImage createImg(File file, int prefix) {
		
		try {
			TraceFile sgyFile = new GprFile();
			sgyFile.open(file);
			
			BackgroundRemovalFilter lf = new BackgroundRemovalFilter();
			lf.removeConstantNoise(sgyFile.getTraces());
			
			AmplitudeMatrix m = new AmplitudeMatrix();
			m.init(sgyFile.getTraces());
			
			m.findLevel();
			
			BufferedImage img = m.getImg();
			return img;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}	
}

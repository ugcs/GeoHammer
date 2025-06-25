package com.ugcs.gprvisualizer.app.commands;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.math.HalfHyperDst;
import com.ugcs.gprvisualizer.math.HorizontalProfile;

public class EdgeSubtractGround implements Command {

	private Model model = AppContext.model;
	private int[] etsum = new int [5];
	
	static class EdgeCoord {
		int index;
		int smp;
		double dist;
		
		public EdgeCoord(int index, int smp, double dist) {
			this.index = index;
			this.smp = smp;
			this.dist = dist;
		}
	}
	
	class EdgeQueue {
		private final List<Trace> list;
		private double foundDst = 0;
		private int lastRemoved = -1;
		private Queue<EdgeCoord> queue = new LinkedList<EdgeCoord>();
		
		public EdgeQueue(List<Trace> list, int edgeType) {
			this.list = list;
		}
		
		void in(int index, int smp, double dist) {
			queue.add(new EdgeCoord(index, smp, dist));
		
			foundDst += dist;
		}		
		
		public void clearGroup(int index, double minDst, int tailIndex) {
			
			if (foundDst > minDst * 0.94) {
				int removeFromIndex = Math.max(lastRemoved + 1, tailIndex);
				
				//clear edges from removeFromIndex to index
				for (EdgeCoord ec : queue) {
					if (ec.index >= removeFromIndex) {
						//mark to delete
						list.get(ec.index).setGood(ec.smp, (byte)1);
					}
				}
				
				lastRemoved = index;
			}			
		}
		
		public void out(int tailIndex) {
			while (!queue.isEmpty() && queue.peek().index < tailIndex) {
				EdgeCoord e = queue.remove();
				foundDst -= e.dist;
			}
		}				
	}	
	
	public EdgeSubtractGround() {

	}

	private static final int MARGIN = 5;
	
	@Override
	public void execute(TraceFile file, ProgressListener listener) {
		if (file.getGroundProfile() == null) {
			System.out.println("!!!!!!!!!!!!!1 file.groundProfile == null");
			return;
		}
		
		List<HorizontalProfile> hplist = new ArrayList<>();
		//straight horizontal line
		hplist.add(getHorizontal(file.getTraces().size()));
		
		// ground profile
		hplist.add(file.getGroundProfile());
		
		
		// ground profile * 2
		hplist.add(multTwice(file.getGroundProfile()));
		
		for (HorizontalProfile hp : hplist) {
			
			int from = -hp.minDeep + MARGIN;
			int to = file.getMaxSamples() - hp.maxDeep - MARGIN;
			
			for (int deep = from; deep < to; deep++) {			
				
				// minimal length of curve which must be similar to hp (cm) 
				double minDst = HalfHyperDst.getGoodSideDstGrnd(
						file, deep + hp.avgdeep, 
						file.getGroundProfile().avgdeep) * 4;
				
				processDeep(file, hp,  deep, minDst);
			}
		}
		
		//real clear
		for (Trace trace : file.getTraces()) {
			for (int smp = 0; smp < trace.numValues(); smp++) {
				if (trace.getGood(smp) == 1) {
					trace.setEdge(smp, (byte)0);
				}
			}
		}
	}

	private HorizontalProfile multTwice(HorizontalProfile groundProfile) {
		HorizontalProfile hp = new HorizontalProfile(groundProfile.deep.length);
		
		for (int i = 0; i < groundProfile.deep.length; i++) {
			hp.deep[i] = groundProfile.deep[i] * 2;
		}
		
		hp.finish(null);
		
		return hp;
	}

	private HorizontalProfile getHorizontal(int size) {

		HorizontalProfile hp = new HorizontalProfile(size);
		
		hp.finish(null);
		
		return hp;
	}

	public int getMaxGroundSmp(TraceFile file) {
		int maxGroundDeep = 0;
		for (Trace trace : file.getTraces()) {
			maxGroundDeep = Math.max(maxGroundDeep, trace.getMaxIndex());
		}
		return maxGroundDeep;
	}

	private void processDeep(TraceFile file, HorizontalProfile hp, int shift, double minDst) {
		List<Trace> list = file.getTraces();
		
		EdgeQueue[] edges = createQueuesForEdgeType(list);
		
		double currentTail = 0;
		int tailIndex = 0;
		for (int i = 0; i < list.size(); i++) {
			
			Trace trace = list.get(i);
			
			currentTail += trace.getPrevDist();
			
			int smp = hp.deep[i] + shift;
			
			// 1 2 3 4 / check range from 0 above to 1 below
			
			for (int r = 0; r <= 1; r++) {
				int realsmp = smp + r;
				int edv = trace.getEdge(realsmp);
				
				edges[edv].in(i, realsmp, trace.getPrevDist());
			}
			
			/// mark edges to remove
			for (int et = 1; et <= 4; et++) {
				edges[et].clearGroup(i, minDst, tailIndex);
			}
			
			/// clean queue tail
			while (currentTail > minDst) {
				currentTail -= list.get(tailIndex).getPrevDist();
				
				//types of edge
				for (int et = 1; et <= 4; et++) {
					edges[et].out(tailIndex);
				}
				
				tailIndex++;
			}
		}		
	}

	public EdgeQueue[] createQueuesForEdgeType(List<Trace> list) {
		EdgeQueue[] edges = new EdgeQueue[5];
		for (int i = 0; i < edges.length; i++) {
			edges[i] = new EdgeQueue(list, i);
		}
		return edges;
	}

	@Override
	public String getButtonText() {

		return "Filter edges";
	}

	@Override
	public WhatChanged.Change getChange() {
		return WhatChanged.Change.traceValues;
	}
}

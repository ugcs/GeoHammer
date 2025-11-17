package com.ugcs.geohammer.map.layer;

import com.ugcs.geohammer.map.RepaintListener;
import org.jspecify.annotations.Nullable;

import java.awt.Dimension;

public abstract class BaseLayer implements Layer {
	
	private boolean active = true;

	@Nullable
	private RepaintListener listener;

	public void setSize(Dimension size) {
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}
	
	public void setRepaintListener(RepaintListener listener) {
		this.listener = listener;
	}

	@Nullable
	public RepaintListener getRepaintListener() {
		return listener;
	}	
}

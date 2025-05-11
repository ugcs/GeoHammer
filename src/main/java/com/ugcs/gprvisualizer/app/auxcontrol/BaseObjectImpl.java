package com.ugcs.gprvisualizer.app.auxcontrol;

import com.ugcs.gprvisualizer.gpr.Model;

import java.awt.Graphics2D;
import java.awt.Rectangle;

public abstract class BaseObjectImpl implements BaseObject {

    private boolean selected = false;

	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	public boolean isSelected() {
		return selected;
	}

	protected void setClip(Graphics2D g2, Rectangle r) {
		g2.setClip(r.x, r.y, r.width, r.height);
	}

}

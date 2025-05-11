package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Image;

import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;
import org.apache.commons.lang3.mutable.MutableInt;

import com.github.thecoldwine.sigrun.common.ext.ProfileField;
import com.github.thecoldwine.sigrun.common.ext.VerticalCutPart;

public class ToggleButton extends DragAnchor {

	Image selectedImg;
	Image unselectedImg;
	private boolean selected = false;
	
	public ToggleButton(Image selectedImg, 
			Image unselectedImg,
			AlignRect alignRect,
			VerticalCutPart offset,
			boolean selected) {
		super(selected ? selectedImg : unselectedImg, alignRect, offset);
		
		this.selectedImg = selectedImg;
		this.unselectedImg = unselectedImg;
		this.setSelected(selected);		
		
	}

	@Override
	protected Image getImg() {
		if (isSelected()) {
			return selectedImg;
		} else {
			return unselectedImg;
		}
	}
	

	@Override
	public boolean mousePressHandle(Point2D localPoint, ScrollableData profField) {
		
		if (isPointInside(localPoint, profField)) {
			
			setSelected(!isSelected());
			
			signal(isSelected());
			return true;
		}
		return false;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}
}

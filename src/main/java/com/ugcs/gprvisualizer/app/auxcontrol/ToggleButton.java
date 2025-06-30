package com.ugcs.gprvisualizer.app.auxcontrol;

import java.awt.Image;

import com.ugcs.gprvisualizer.app.ScrollableData;
import javafx.geometry.Point2D;

public class ToggleButton extends DragAnchor {

	Image selectedImg;
	Image unselectedImg;
	private boolean selected = false;
	
	public ToggleButton(Image selectedImg, 
			Image unselectedImg,
			AlignRect alignRect,
			boolean selected) {
		super(selected ? selectedImg : unselectedImg, alignRect);
		
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

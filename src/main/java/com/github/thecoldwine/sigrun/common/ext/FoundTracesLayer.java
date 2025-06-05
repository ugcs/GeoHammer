package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.draw.Layer;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.geometry.Point2D;
import javafx.scene.Node;

public class FoundTracesLayer implements Layer {

	private Model model;
	private Color pointColor = Color.GREEN;
	
	public FoundTracesLayer(Model model) {
		this.model = model;
	}

	@Override
	public List<Node> getToolNodes() {
		return List.of();
	}

	@Override
	public void draw(Graphics2D g2, MapField fixedField) {
		g2.setColor(pointColor);
		
		for (BaseObject bo : model.getAuxElements()) {
			bo.drawOnMap(g2, fixedField);
		}

		// TODO GPR_LINES
		// create a new click place
		/*
		for (ClickPlace clickPlace : model.getSelectedTraces()) {
			clickPlace.drawOnMap(g2, fixedField);
		}
		*/
	}

	@Override
	public boolean mousePressed(Point2D point) {
		for(BaseObject bo : model.getAuxElements()) {
			if(bo.mousePressHandle(point, model.getMapField())) {
				return true;
			}			
		}
		return false;
	}
}

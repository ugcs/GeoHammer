package com.ugcs.geohammer.map.layer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;

import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.SelectedTrace;
import com.ugcs.geohammer.model.TraceSelectionType;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.ClickPlace;
import com.ugcs.geohammer.model.Model;

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

		for (SelectedTrace selected : model.getSelectedTraces()) {
			if (selected.selectionType() != TraceSelectionType.USER) {
				continue;
			}
			ClickPlace clickPlace = new ClickPlace(selected.trace(), selected.selectionType());
			clickPlace.drawOnMap(g2, fixedField);
		}
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

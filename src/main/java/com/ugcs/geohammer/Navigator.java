package com.ugcs.geohammer;

import java.util.List;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.ActivationPolicy;
import com.ugcs.geohammer.model.ToolNode;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.model.Model;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

@Component
public class Navigator implements ToolProducer {

	private final Model model;
	private SgyFile currentFile;

	public Navigator(Model model) {
		this.model = model;
	}

	@Override
	public List<ToolNode> getToolNodes() {
		Button backBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ARROW_LEFT, new Button());
		Button fitBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.FIT, new Button());
		Button nextBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ARROW_RIGHT, new Button());

		backBtn.setTooltip(new Tooltip("Fit previous line to window"));
		fitBtn.setTooltip(new Tooltip("Fit current line to window"));
		nextBtn.setTooltip(new Tooltip("Fit next line to window"));

		fitBtn.setOnAction(e -> fitCurrent());
		backBtn.setOnAction(e -> fitBack());
		nextBtn.setOnAction(e -> fitNext());

		return List.of(
				new ToolNode(backBtn, ActivationPolicy.fileSelected()),
				new ToolNode(fitBtn, ActivationPolicy.fileSelected()),
				new ToolNode(nextBtn, ActivationPolicy.fileSelected()));
	}

	public void fitNext() {
		Chart chart = model.getChart(currentFile);
		if (chart != null) {
			chart.zoomToNextLine();
		}
	}

	public void fitBack() {
		Chart chart = model.getChart(currentFile);
		if (chart != null) {
			chart.zoomToPreviousLine();
		}
	}

	public void fitCurrent() {
		Chart chart = model.getChart(currentFile);
		if (chart != null) {
			chart.zoomToCurrentLine();
		}
	}

	@EventListener
	public void onFileSelected(FileSelectedEvent event) {
		currentFile =  event.getFile();
	}
}

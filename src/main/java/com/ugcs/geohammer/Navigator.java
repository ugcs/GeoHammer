package com.ugcs.geohammer;

import java.util.Arrays;
import java.util.List;

import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.model.event.FileSelectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.model.Model;

import javafx.scene.Node;
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
	public List<Node> getToolNodes() {
		Button backBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ARROW_LEFT, new Button());
		//new Button("", ResourceImageHolder.getImageView("arrow_left_20.png"));
		Button fitBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.FIT, new Button());
		//new Button("", ResourceImageHolder.getImageView("fit_20.png"));		
		Button nextBtn = ResourceImageHolder.setButtonImage(ResourceImageHolder.ARROW_RIGHT, new Button());
		//new Button("", ResourceImageHolder.getImageView("arrow_right_20.png"));
		
		backBtn.setTooltip(new Tooltip("Fit previous line to window"));
		fitBtn.setTooltip(new Tooltip("Fit current line to window"));
		nextBtn.setTooltip(new Tooltip("Fit next line to window"));
		
		fitBtn.setOnAction(e -> {
			fitCurrent();
		});

		backBtn.setOnAction(e -> {
			fitBack();
		});

		nextBtn.setOnAction(e -> {
			fitNext();
		});
		
		return Arrays.asList(backBtn, fitBtn, nextBtn);
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

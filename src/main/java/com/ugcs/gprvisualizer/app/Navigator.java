package com.ugcs.gprvisualizer.app;

import java.util.Arrays;
import java.util.List;

import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.draw.ToolProducer;
import com.ugcs.gprvisualizer.gpr.Model;

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
		Chart chart = model.getFileChart(currentFile);
		if (chart != null) {
			chart.zoomToNextLine();
		}
	}

	public void fitBack() {
		Chart chart = model.getFileChart(currentFile);
		if (chart != null) {
			chart.zoomToPreviousLine();
		}
	}

	public void fitCurrent() {
		Chart chart = model.getFileChart(currentFile);
		if (chart != null) {
			chart.zoomToCurrentLine();
		}
	}

	@EventListener
	public void onFileSelected(FileSelectedEvent event) {
		currentFile =  event.getFile();
	}
}

package com.ugcs.gprvisualizer.app;

import java.util.Arrays;
import java.util.List;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.auxcontrol.ClickPlace;
import com.ugcs.gprvisualizer.event.FileSelectedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
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
		if (currentFile instanceof CsvFile csvFile) {
			model.getChart(csvFile).ifPresent(sensorLineChart -> {
				sensorLineChart.zoomToNextLine();
			});
		} else {
			var chart = model.getProfileField(currentFile);
			var selectedFile = chart.getField().getSgyFileByTrace(chart.getMiddleTrace());
			fitFile(chart.getField().getNextSgyFile(selectedFile));
		}
	}

	public void fitBack() {
		if (currentFile instanceof CsvFile csvFile) {
			model.getChart(csvFile).ifPresent(sensorLineChart -> {
				sensorLineChart.zoomToPreviousLine();
			});
		} else {
			var chart = model.getProfileField(currentFile);
			var selectedFile = chart.getField().getSgyFileByTrace(chart.getMiddleTrace());
			fitFile(chart.getField().getPrevSgyFile(selectedFile));
		}
	}

	public void fitCurrent() {
		if (currentFile instanceof CsvFile csvFile) {
			model.getChart(csvFile).ifPresent(sensorLineChart -> {
				sensorLineChart.zoomToCurrentLine();
			});
		} else {
			var chart = model.getProfileField(currentFile);
			// if there's a selection mark present on the chart
			// zoom to a file containing that mark;
			// otherwise zoom to a file containing
			// the middle trace
			SgyFile selectedFile;
			ClickPlace mark = model.getSelectedTrace(chart);
			if (mark != null) {
				selectedFile = mark.getTrace().getFile();
			} else {
				selectedFile = chart.getField().getSgyFileByTrace(chart.getMiddleTrace());
			}
			fitFile(selectedFile);
		}
	}

	private void fitFile(SgyFile sgyFile) {
		if (sgyFile == null) {
			return;
		}
		
		model.getProfileField(sgyFile).setMiddleTrace(
				(sgyFile.getOffset().getStartTrace() 
				+ sgyFile.getOffset().getFinishTrace()) 
				/ 2);
		
		int maxSamples = sgyFile.getOffset().getMaxSamples();
		int tracesCount = sgyFile.getTraces().size(); 
		
		model.getProfileField(sgyFile).fit(maxSamples, tracesCount);
		
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	@EventListener
	public void onFileSelected(FileSelectedEvent event) {
		currentFile =  event.getFile();
	}

}

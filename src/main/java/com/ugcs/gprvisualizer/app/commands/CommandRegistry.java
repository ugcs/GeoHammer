package com.ugcs.gprvisualizer.app.commands;

import java.util.function.Consumer;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.event.WhatChanged;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.ProgressTask;
import com.ugcs.gprvisualizer.app.TaskRunner;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.scene.control.Button;


@Component
public class CommandRegistry {
	
	@Autowired
	private ApplicationEventPublisher eventPublisher;

	@Autowired
	private Model model;

	@Autowired
	private Status status; 
	
	private static final ProgressListener emptyListener = new ProgressListener() {
		
		@Override
		public void progressPercent(int percent) {
			
		}
		
		@Override
		public void progressMsg(String msg) {
			
		}

		@Override
		public void progressSubMsg(String msg) {
			// TODO Auto-generated method stub
			
		}
	};
	
	public void runForGprFiles(Command command) {		
		runForGprFiles(command, emptyListener);
	}
	
	private void runForGprFiles(Command command, ProgressListener listener) {
	
		int number = 1;
		var files = model.getFileManager().getGprFiles();
		int count = files.size();
		
		for (TraceFile sgyFile : files) {
			
			try {
				listener.progressMsg("process file '" 
						+ sgyFile.getFile().getName() + "' (" + number++ + "/" + count + ")"  );
				
				command.execute(sgyFile, listener);
				
			} catch (Exception e) {
				e.printStackTrace();
				
				listener.progressMsg("error");
			}
		}
		
		WhatChanged.Change ch = command.getChange();
		if (ch != null) {
			eventPublisher.publishEvent(new WhatChanged(command, ch));
		}
		
		listener.progressMsg("process finished '" + command.getButtonText() + "'");
	}

	public Button createButton(Command command) {
		return createButton(command, null);
	}
	
	public Button createButton(Command command, String iconName, Consumer<Object> finish) {
		Button button = new Button(command.getButtonText());
		
		if (StringUtils.isNotBlank(iconName)) {
			button.setGraphic(ResourceImageHolder.getImageView(iconName));
		}
		
		button.setOnAction(e -> {
			runForGprFiles(command);
			
			if (finish != null) {
				finish.accept(null);
			}
		});
		
		return button;
	}
	
	public Button createButton(Command command, Consumer<Object> finish) {
		return createButton(command, null, finish);
	}
}

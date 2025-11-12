package com.ugcs.geohammer.service.gpr;

import java.util.List;
import java.util.function.Consumer;

import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.event.WhatChanged;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.model.ProgressListener;
import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.model.Model;

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
	
	public void runForGprFiles(List<TraceFile> files, Command command) {
		runForGprFiles(files, command, emptyListener);
	}
	
	private void runForGprFiles(List<TraceFile> files, Command command, ProgressListener listener) {
	
		int number = 1;
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
			runForGprFiles(model.getFileManager().getGprFiles(), command);
			
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

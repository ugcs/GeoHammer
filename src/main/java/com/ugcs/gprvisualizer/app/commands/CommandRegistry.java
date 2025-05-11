package com.ugcs.gprvisualizer.app.commands;

import java.util.function.Consumer;

import com.ugcs.gprvisualizer.event.WhatChanged;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.ProgressTask;
import com.ugcs.gprvisualizer.app.TaskRunner;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;


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
		
		for (SgyFile sgyFile : files) {
			
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
		
		//System.out.println("finished command " + command.getButtonText() );
	}
	
	
	public Button createAsinqTaskButton(AsinqCommand command) {
		return createAsinqTaskButton(command, null);
	}
	
	public Button createAsinqTaskButton(AsinqCommand command, Consumer<Object> finish) {
		
		Button button = new Button(command.getButtonText());
		
		ProgressTask task = listener -> {
				runForGprFiles(command, listener);				
				
				if (finish != null) {
					finish.accept(null);
				}
		};
		
		button.setOnAction(event -> new TaskRunner(status, task).start());
		
		return button;
	}

	public Button createAsinqTaskButton(SingleCommand command, Consumer<Object> finish) {
		
		Button button = new Button(command.getButtonText());

		ProgressTask task = new ProgressTask() {
			
			@Override
			public void run(ProgressListener listener) {
				try {
					listener.progressMsg("start processing");
					
                    command.execute(AppContext.model.getFileManager().getGprFiles(),
							listener);
					
				} catch (Exception e) {
					e.printStackTrace();
					
					listener.progressMsg("error");
				}
				
				WhatChanged.Change ch = command.getChange();
				if (ch != null) {
					eventPublisher.publishEvent(new WhatChanged(command, ch));
				}
				
				listener.progressMsg("process finished '" 
						+ command.getButtonText() + "'");
					
				if (finish != null) {
					finish.accept(null);
				}
			}
		};
		
		button.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
				
				new TaskRunner(status, task).start();
		    	
		    }
		});
		
		return button;
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
	
	public static Button createButton(String title, EventHandler<ActionEvent> action) {
		
		Button button = new Button(title);
		button.setOnAction(action);
		
		return button;
	}
	
	public static Button createButton(String title, Node img, String tooltip, EventHandler<ActionEvent> action) {
		
		Button button = new Button(title, img);
		button.setTooltip(new Tooltip(tooltip));
		button.setOnAction(action);
		
		return button;
	}
	
	
}

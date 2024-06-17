package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.MarkupFile;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.draw.Change;
import com.ugcs.gprvisualizer.draw.ToolProducer;
import com.ugcs.gprvisualizer.draw.WhatChanged;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;

@Component
public class Saver implements ToolProducer, InitializingBean {

	private final Button buttonSave = ResourceImageHolder.setButtonImage(ResourceImageHolder.SAVE, new Button());
	private final Button buttonSaveTo = ResourceImageHolder.setButtonImage(ResourceImageHolder.SAVE_TO, new Button());

	{
		buttonSave.setTooltip(new Tooltip("Save"));
		buttonSaveTo.setTooltip(new Tooltip("Save to.."));
	}	
	
	@Autowired
	private Model model;
	
	@Autowired
	private Loader loader;
	
	@Autowired
	private Status status;

	@Autowired
	private Broadcast broadcast;
	
	private File folder;
	
	private ProgressTask saveTask = listener -> {

		listener.progressMsg("save now");
		List<File> newfiles = saveTheSame();		
		
		listener.progressMsg("load now");			
		try {
			loader.load(newfiles, listener);			
		} catch (Exception e) {
			MessageBoxHelper.showError("error reopening files", "");
		}
	    	
		broadcast.notifyAll(new WhatChanged(Change.fileopened));
	    	
	    status.showProgressText("saved " 
	    		+ model.getFileManager().getFilesCount() + " files");
	};

	private ProgressTask saveAsTask = listener -> {

		listener.progressMsg("save now");
		List<File> newfiles = saveAs(folder);
		
		listener.progressMsg("load now");
		try {
			loader.load(newfiles, listener);
		} catch (Exception e) {			
			e.printStackTrace();
			MessageBoxHelper.showError("error reopening files", "");
		}
				
		broadcast.notifyAll(new WhatChanged(Change.fileopened));
		
		status.showProgressText("saved " 
				+ model.getFileManager().getFilesCount() + " files");
	};

	@Override
	public void afterPropertiesSet() throws Exception {

		buttonSave.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
				new TaskRunner(status, saveTask).start();
		    	
		    }
		});
		
		buttonSaveTo.setOnAction(new EventHandler<ActionEvent>() {
		    @Override public void handle(ActionEvent e) {
		    	
		    	DirectoryChooser dirChooser = new DirectoryChooser(); 
				//TODO: тут надо будет получать выбранный файл
		    	if (!model.getFileManager().getGprFiles().isEmpty()) {
		    		
		    		SgyFile firstFile = model.getFileManager().getGprFiles().get(0);
		    		
					dirChooser.setInitialDirectory(
		    				firstFile.getFile().getParentFile());
		    	}

		    	folder = dirChooser.showDialog(AppContext.stage); 
		    	  
                if (folder != null) { 
                	new TaskRunner(status, saveAsTask).start();
                } 		    	
		    }
		});
	}
	
	public Saver(Model model) {
		this.model = model;		
	}
	
	@Override
	public List<Node> getToolNodes() {		
		return List.of(buttonSave, buttonSaveTo);
	}
	
	private List<File> saveTheSame() {
		List<File> newfiles = new ArrayList<>();
		
		for (SgyFile file : model.getFileManager().getGprFiles()) {
			newfiles.add(save(file));
		}

		for (SgyFile file : model.getFileManager().getCsvFiles()) {
			newfiles.add(save(file));
		}
		
		return newfiles;
	}
	
	private List<File> saveAs(File folder) {
		List<File> newfiles = new ArrayList<>();
		
		for (SgyFile file : model.getFileManager().getGprFiles()) {
			newfiles.add(save(file, folder));
		}
		
		return newfiles;
	}

	private File save(SgyFile sgyFile, File folder) {

		File newFile = null;
		
		try {
			File oldFile = sgyFile.getFile();
			
			newFile = new File(folder, oldFile.getName());
			
			sgyFile.setFile(newFile);
			
			sgyFile.save(newFile);
			
			sgyFile.saveAux(newFile);
			
			new MarkupFile().save(sgyFile, newFile);
			
		} catch (Exception e) {
			e.printStackTrace();
			newFile = null;
		}
		return newFile;
	}
	
	private File save(SgyFile sgyFile) {

		File oldFile = null;
		
		try {
			oldFile = sgyFile.getFile();
			File nfolder = oldFile.getParentFile();


			String suffix = oldFile.getName().substring(
					oldFile.getName().lastIndexOf("."));
			
			File tmp = File.createTempFile("tmp", suffix, nfolder);
			
			sgyFile.save(tmp);
			
			boolean t = oldFile.delete();
			if (!t) {
				System.out.println("!!!   delete problem!");
			}
			
			boolean r = tmp.renameTo(oldFile);
			
			if (!r) {
				System.out.println("!!!   rename problem!");
			}
			
			if (suffix.contains("sgy")) {
				sgyFile.saveAux(oldFile);
				new MarkupFile().save(sgyFile, oldFile);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			oldFile = null;
		}
		return oldFile;
	}

}

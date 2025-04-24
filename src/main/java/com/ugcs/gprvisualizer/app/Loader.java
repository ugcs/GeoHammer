package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.util.List;

import com.ugcs.gprvisualizer.app.kml.KmlReader;

import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.ConstPointsFile;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;

import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.app.parcers.exceptions.CSVParsingException;

import com.ugcs.gprvisualizer.gpr.Model;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

@Component
public class Loader {

	private final Model model;
	private final Status status; 
	private final ApplicationEventPublisher eventPublisher;

	@Autowired
	public Loader(Model model, Status status, ApplicationEventPublisher eventPublisher) {
		this.model = model;
		this.status = status;
		this.eventPublisher = eventPublisher;
	}
	
	public EventHandler<DragEvent> getDragHandler() {
		return dragHandler;
	}
	
	public EventHandler<DragEvent> getDropHandler() {
		return dropHandler;
	}
	
	private final EventHandler<DragEvent> dragHandler = new EventHandler<DragEvent>() {

        @Override
        public void handle(DragEvent event) {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        }
    };

	/**
	 * Attempts to load and process the specified list of files based on their type.
	 * The method supports processing `constPoints`, KML, and CSV files. If the file
	 * type is recognized, it invokes the appropriate file-handling logic. For unsupported
	 * or unprocessed cases, the method initializes a background task with progress
	 * tracking to handle the file processing.
	 *
	 * @param files the list of files to be loaded; each file is evaluated to determine
	 *              its type and processed accordingly
	 * @return true if the files are successfully loaded and processed; false if need some aditional logic for processing.
	 */
	public boolean load(List<File> files) {
		if (isConstPointsFile(files)) {
			openConstPointFile(files);
			return true;
		}

		if (isKmlFile(files)) {
			openKmlFile(files);
			return true;
		}

		if (isCsvFile(files)) {
			model.setLoading(true);
			openCSVFiles(files);
			model.publishEvent(new FileOpenedEvent(this, files));
			model.setLoading(false);
			return true;
		}

		//TODO: fix unsaved for the CSV files
		//if (model.stopUnsaved()) {
		//	return;
		//}

		ProgressTask loadTask = new ProgressTask() {
			@Override
			public void run(ProgressListener listener) {
				try {

					loadWithNotify(files, listener);

				} catch (Exception e) {
					e.printStackTrace();

					MessageBoxHelper.showError(
							"Can`t open files",
							"Probably file has incorrect format");

					model.closeAllCharts();
					//model.getFileManager().getFiles().clear();
					model.getChartsContainer().getChildren().clear();

					model.updateAuxElements();
					model.initField();
				}
			}
		};

		new TaskRunner(status, loadTask).start();
		return false;
	}

	private void openConstPointFile(final List<File> files) {
		ConstPointsFile cpf = new ConstPointsFile();
		cpf.load(files.get(0));

		for (SgyFile sgyFile : model.getFileManager().getGprFiles()) {
			cpf.calcVerticalCutNearestPoints(sgyFile);
		}

		model.updateAuxElements();
	}
    
    private final EventHandler<DragEvent> dropHandler = new EventHandler<DragEvent>() {
        @Override
        public void handle(DragEvent event) {
        	
        	Dragboard db = event.getDragboard();
        	if (!db.hasFiles()) {
        		return;
        	}
        	
         	final List<File> files = db.getFiles();

			if (load(files)) return;

			event.setDropCompleted(true);
            event.consume();
        }
    };

	private void openCSVFiles(List<File> files) {
		try {
			//SgyFile sgyFile = model.getFileManager().getFiles().size() > 0 ? 
			//	model.getFileManager().getFiles().get(0) : new GprFile();
			for (File file: files) {
				CsvFile csvFile = new CsvFile(model.getFileManager().getFileTemplates());
				csvFile.open(file);

				if (model.getChart(csvFile).isEmpty()) {

					model.getFileManager().addFile(csvFile);	

					//model.init();			
		
					//when open file by dnd (not after save)
					model.initField();

					csvFile.updateTraces();

					model.initChart(csvFile);

					model.updateAuxElements();
				}
			}
		} catch (Exception e) {
			if (e instanceof CSVParsingException cpe) {
				cpe.printStackTrace();
				MessageBoxHelper.showError(
						"Can`t open file " + cpe.getFile().getName(), "File has incorrect format: " + cpe.getMessage());
			} else {
				e.printStackTrace();
				MessageBoxHelper.showError(
						"Can`t open file", e.getMessage() != null ? e.getMessage() :
								"Probably file has incorrect format");
			}
		}
		
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.updateButtons));
	}

	private void openKmlFile(List<File> files) {
		if (model.getFileManager().getGprFiles().isEmpty()) {
			MessageBoxHelper.showError(
				"Can`t open kml file",
				"Open GPR file at first");
			return;
		}
		if (files.size() > 1) {
			MessageBoxHelper.showError(
				"Can`t open position file",
				"Only one position file must be opened");
			return;
		}

		try {
			new KmlReader().read(files.get(0), model);
		} catch (Exception e) {

			e.printStackTrace();
			MessageBoxHelper.showError(
				"Can`t open position file",
				"Probably file has incorrect format");
		}

		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.updateButtons));
		eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	public void loadWithNotify(final List<File> files, ProgressListener listener)
			throws Exception {
		load(files, listener);
		eventPublisher.publishEvent(new FileOpenedEvent(this, files));
	}
    
	public void load(final List<File> files, ProgressListener listener) 
			throws Exception {
		
		int filesCountBefore = model.getFileManager().getFilesCount();
		try {
			model.setLoading(true);
			loadInt(files, listener);
		} finally {
			model.setLoading(false);
		}
		
		int loadedFiles = model.getFileManager().getFilesCount() - filesCountBefore;

		if (loadedFiles > 0) {
			status.showProgressText("loaded " 
				+ model.getFileManager().getFilesCount() + " files");
		} else {
			status.showProgressText("no files loaded");
		
		}
	}        		
    
	private void loadInt(List<File> files, ProgressListener listener) throws Exception {
		/// clear
		//model.getAuxElements().clear();
		//model.getChanges().clear();
		
		listener.progressMsg("load");

		if (isCsvFile(files)) {
			openCSVFiles(files);
		} else {
			model.getFileManager().processList(files, listener);
			//model.closeAllCharts();
			model.init();			
		
			//when open file by dnd (not after save)
			model.initField();	
		
			// FIXME: not need? remove it
			/*SgyFile file = model.getFileManager().getGprFiles().get(0);
			if (file.getSampleInterval() < 105) {
				model.getSettings().hyperkfc = 25;
			} else {
				double i = file.getSampleInterval() / 104.0;
				model.getSettings().hyperkfc = (int) (25.0 + i * 1.25);
			}*/
		}			
	}

	private boolean isConstPointsFile(final List<File> files) {
		return files.size() == 1 
				&& files.get(0).getName().endsWith(".constPoints");
	}
	
	private boolean isCsvFile(final List<File> files) {
		return !files.isEmpty() 
				&& (files.get(0).getName().toLowerCase().endsWith(".csv")
				|| files.get(0).getName().toLowerCase().endsWith(".asc"));
	}

	private boolean isKmlFile(final List<File> files) {
		return !files.isEmpty()
			&& files.get(0).getName().toLowerCase().endsWith(".kml");
	}
}

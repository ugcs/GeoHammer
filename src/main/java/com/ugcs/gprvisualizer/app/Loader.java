package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.PositionFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.kml.KmlReader;

import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.dzt.DztFile;
import com.ugcs.gprvisualizer.event.FileOpenErrorEvent;
import com.ugcs.gprvisualizer.event.FileOpenedEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileTypes;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.ConstPointsFile;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;

import com.ugcs.gprvisualizer.app.intf.Status;

import com.ugcs.gprvisualizer.gpr.Model;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

@Component
public class Loader {

	private static final Logger log = LoggerFactory.getLogger(Loader.class);

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

	private final EventHandler<DragEvent> dragHandler = event -> {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
        }
        event.consume();
    };

	private final EventHandler<DragEvent> dropHandler = event -> {
        Dragboard dragboard = event.getDragboard();
        if (!dragboard.hasFiles()) {
            return;
        }

        List<File> files = dragboard.getFiles();
        if (load(files)) {
			return;
		}

        event.setDropCompleted(true);
        event.consume();
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
		ProgressTask loadTask = listener -> {
			List<File> openedFiles = new ArrayList<>();

			for (File file : prepareOpenFiles(files)) {
				model.setLoading(true);
				try {
					listener.progressMsg("Opening " + file);

					boolean opened = openFile(file);
					if (opened) {
						listener.progressMsg("File opened: " + file);
						openedFiles.add(file);
					}
				} catch (Exception e) {
					log.error("Error", e);
					listener.progressMsg("Error: " + e.getMessage());
					eventPublisher.publishEvent(new FileOpenErrorEvent(this, file, e));
					MessageBoxHelper.showError(
							"Can`t open file " + file.getName(),
							Strings.nullToEmpty(e.getMessage()));
				} finally {
					model.setLoading(false);
				}
			}

			if (!openedFiles.isEmpty()) {
				eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.updateButtons));
				eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
				eventPublisher.publishEvent(new FileOpenedEvent(this, files));
			} else {
				listener.progressMsg("No files loaded");
			}
        };

		new TaskRunner(status, loadTask).start();
		return false;
	}

	private List<File> prepareOpenFiles(List<File> files) {
		// make unique and sort by name
		List<File> result = new ArrayList<>(new HashSet<>(Nulls.toEmpty(files)));
		result.sort(Comparator.comparing(File::getName));
		// expand directories
		result = expandDirectories(result);
		return result;
	}

	private List<File> expandDirectories(List<File> files) {
		List<File> result = new ArrayList<>();
		for (File file : Nulls.toEmpty(files)) {
			if (file.isFile()) {
				result.add(file);
			} else {
				result.addAll(listFiles(file));
			}
		}
		return result;
	}

	private List<File> listFiles(File directory) {
		if (directory == null || !directory.isDirectory()) {
			return List.of();
		}
		FilenameFilter filter = (dir, name)
				-> FileTypes.isGprFile(name) || FileTypes.isDztFile(name);
		File[] files = directory.listFiles(filter);
		if (files == null) {
			return List.of();
		}
		// exclude directories from result
		List<File> result = new ArrayList<>(files.length);
		for (File file : files) {
			if (file.isFile()) {
				result.add(file);
			}
		}
		// sort files by name
		result.sort(Comparator.comparing(File::getName));
		return result;
	}

	private boolean openFile(File file) throws IOException {
		if (file == null) {
			return false;
		}
		if (FileTypes.isGprFile(file)) {
			openGprFile(file);
			return true;
		}
		if (FileTypes.isDztFile(file)) {
			openDztFile(file);
			return true;
		}
		if (FileTypes.isCsvFile(file)) {
			openCsvFile(file);
			return true;
		}
		if (FileTypes.isKmlFile(file)) {
			openKmlFile(file);
			return true;
		}
		if (FileTypes.isConstPointFile(file)) {
			openConstPointFile(file);
			return true;
		}
		return false;
	}

	private void openGprFile(File file) throws IOException {
		Check.notNull(file);

		TraceFile gprFile = new GprFile();
		gprFile.open(file);

		model.getFileManager().addFile(gprFile);

		// positions
		FileTemplates templates = model.getFileManager().getFileTemplates();
		try {
			new PositionFile(templates).load(gprFile);
		} catch (Exception e) {
			log.warn("Error loading positions file", e);
		}

		model.updateAuxElements();
		model.initField();
	}

	private void openDztFile(File file) throws IOException {
		Check.notNull(file);

		DztFile dztFile = new DztFile();
		dztFile.open(file);

		model.getFileManager().addFile(dztFile);
		model.updateAuxElements();
		model.initField();
	}

	private void openCsvFile(File file) throws IOException {
		Check.notNull(file);

		CsvFile csvFile = new CsvFile(model.getFileManager().getFileTemplates());
		csvFile.open(file);

		if (model.getCsvChart(csvFile).isEmpty()) {
			model.getFileManager().addFile(csvFile);
			model.updateAuxElements();
			model.initCsvChart(csvFile);
			model.initField();
		} else if (csvFile.getFile() != null && Objects.equals(file.getAbsolutePath(), csvFile.getFile().getAbsolutePath())) {
			model.getCsvChart(csvFile).get().close(true);
			model.getFileManager().addFile(csvFile);
			model.updateAuxElements();
			model.initCsvChart(csvFile);
			model.initField();
		}
	}

	private void openKmlFile(File file) {
		Check.notNull(file);

		if (model.getFileManager().getGprFiles().isEmpty()) {
			throw new IllegalStateException("Open GPR file first");
		}

		new KmlReader().read(file, model);
	}

	private void openConstPointFile(File file) {
		Check.notNull(file);

		ConstPointsFile constPointFile = new ConstPointsFile();
		constPointFile.load(file);

		for (TraceFile traceFile : model.getFileManager().getGprFiles()) {
			constPointFile.calcVerticalCutNearestPoints(traceFile);
		}

		model.updateAuxElements();
	}
}

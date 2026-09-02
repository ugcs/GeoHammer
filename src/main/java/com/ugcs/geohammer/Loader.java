package com.ugcs.geohammer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;

import com.ugcs.geohammer.format.FileOpenException;
import com.ugcs.geohammer.format.csv.parser.Parser;
import com.ugcs.geohammer.format.csv.parser.Warnings;
import com.ugcs.geohammer.format.gpr.GprFile;
import com.ugcs.geohammer.format.meta.MetaFiles;
import com.ugcs.geohammer.format.nmea.NmeaFile;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.ProgressTask;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.TraceFile;

import com.ugcs.geohammer.format.dzt.DztFile;
import com.ugcs.geohammer.model.event.FileOpenErrorEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.template.TemplateEditor;
import com.ugcs.geohammer.service.TaskRunner;
import com.ugcs.geohammer.service.TaskService;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.status.Status;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.format.csv.CsvFile;

import com.ugcs.geohammer.model.Model;

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

	private final TaskService taskService;

	private final ExecutorService executor;

	private final TemplateEditor templateEditor;

	@Autowired
	public Loader(Model model, Status status, ApplicationEventPublisher eventPublisher,
			TaskService taskService, ExecutorService executor, TemplateEditor templateEditor) {
		this.model = model;
		this.status = status;
		this.eventPublisher = eventPublisher;
		this.taskService = taskService;
		this.executor = executor;
		this.templateEditor = templateEditor;
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
        load(files);

        event.setDropCompleted(true);
        event.consume();
    };

	public void load(List<File> files) {
		if (files.isEmpty()) {
			return;
		}

		ProgressTask loadTask = listener -> {
			List<File> openedFiles = new ArrayList<>();

			for (File file : prepareOpenFiles(files)) {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}

				model.setLoading(true);
				try {
					listener.progressMsg("Opening " + file);

					boolean opened = openFile(file);
					if (opened) {
						listener.progressMsg("File opened: " + file);
						openedFiles.add(file);
					}
				} catch (CancellationException e) {
					// loading cancelled
					log.warn("Loading cancelled", e);
					break;
				} catch (Exception e) {
					log.error("Error", e);
					listener.progressMsg("Error: " + e.getMessage());

					eventPublisher.publishEvent(new FileOpenErrorEvent(this, file, e));
					Dialogs.showError(
							"Can't open file " + file.getName(),
							new FileOpenException(file, e));
				} finally {
					model.setLoading(false);
				}
			}

			if (!openedFiles.isEmpty()) {
				// run in app thread to wait for open postponed tasks
				Platform.runLater(() -> {
					eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.updateButtons));
					eventPublisher.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
					eventPublisher.publishEvent(new FileOpenedEvent(this, openedFiles));
				});
			} else {
				listener.progressMsg("No files loaded");
			}
        };

		String taskName = files.size() == 1
				? "Loading " + files.getFirst().getName()
				: "Loading " + files.size()	+ " files";

		TaskRunner runner = new TaskRunner(status, loadTask);
		var future = executor.submit(() -> {
			runner.start(false);
		});
		taskService.registerTask(future, taskName);
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
		FilenameFilter filter = (dir, name) -> FileTypes.isTraceFile(new File(dir, name));
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
		if (MetaFiles.isMeta(file)) {
			return openMetaSource(file);
		}
		return openSourceFile(file);
	}

	private boolean openMetaSource(File metaFile) throws IOException {
		IOException firstError = null;
		for (File source : MetaFiles.getSources(metaFile)) {
			if (Thread.currentThread().isInterrupted()) {
				return false;
			}
			try {
				if (openSourceFile(source)) {
					return true;
				}
			} catch (IOException e) {
				log.warn("Error opening {}", source, e);
				if (firstError == null) {
					firstError = e;
				}
			}
		}
		if (firstError != null) {
			throw firstError;
		}
		return false;
	}

	private boolean openSourceFile(File file) throws IOException {
		if (FileTypes.isCsvFile(file)) {
			openCsvFile(file);
			return true;
		}
		if (FileTypes.isGprFile(file)) {
			openGprFile(file);
			return true;
		}
		if (FileTypes.isDztFile(file)) {
			openDztFile(file);
			return true;
		}
		if (FileTypes.isSvlogFile(file)) {
			openSvlogFile(file);
			return true;
		}
		if (FileTypes.isNmeaFile(file)) {
			openNmeaFile(file);
			return true;
		}
		// try csv as a fallback for text formats only
		if (FileTypes.isTextFile(file)) {
			openCsvFile(file);
			return true;
		}
		throw new IOException("Unsupported file format: " + file.getName());
	}

	private void openGprFile(File file) throws IOException {
		Check.notNull(file);

		TraceFile gprFile = new GprFile();

		gprFile.open(file);

		// positions
		try {
            gprFile.loadPositionFile(model.getFileManager().getFileTemplates());
		} catch (Exception e) {
			log.warn("Error loading positions file", e);
		}

		Platform.runLater(() -> {
            model.initChart(gprFile);
		});
	}

	private void openDztFile(File file) throws IOException {
		Check.notNull(file);

		DztFile dztFile = new DztFile();

		dztFile.open(file);

		Platform.runLater(() -> {
            model.initChart(dztFile);
		});
	}

	private void openCsvFile(File file) throws IOException {
		Check.notNull(file);

		FileTemplates fileTemplates = model.getFileManager().getFileTemplates();
		Template template = fileTemplates.findTemplate(file);
		if (template == null) {
			template = templateEditor.createTemplate(file);
			if (template == null) {
				throw new RuntimeException("Can't find template for file " + file.getName());
			}
		}

		CsvFile csvFile = new CsvFile(fileTemplates);
		csvFile.open(file, template);

		Parser parser = csvFile.getParser();
		if (parser != null) {
			Warnings warnings = parser.getWarnings();
			if (!warnings.isEmpty()) {
				Dialogs.showWarning("Warnings in " + file.getName(), warnings.format());
			}
		}

		Platform.runLater(() -> {
            model.initChart(csvFile);
		});
	}

	private void openSvlogFile(File file) throws IOException {
        Check.notNull(file);

        SonarFile sonarFile = new SonarFile();

        sonarFile.open(file);

        Platform.runLater(() -> {
            model.initChart(sonarFile);
        });
    }

	private void openNmeaFile(File file) throws IOException {
		Check.notNull(file);

		NmeaFile nmeaFile = new NmeaFile();

		nmeaFile.open(file);

		Platform.runLater(() -> {
			model.initChart(nmeaFile);
		});
	}

	public void loadFrom(SgyFile sgyFile, File file) throws IOException {
		Check.notNull(sgyFile);
		Check.notNull(file);

		switch (sgyFile) {
			case CsvFile csvFile -> {
				CsvFile temp = new CsvFile(model.getFileManager().getFileTemplates());
				// keep the template of the source file: it may be unsaved
				Parser parser = csvFile.getParser();
				if (parser != null) {
					temp.open(file, parser.getTemplate());
				} else {
					temp.open(file);
				}
				csvFile.loadFrom(temp);
			}
			case GprFile gprFile -> {
				GprFile temp = new GprFile();
				temp.open(file);
				gprFile.loadFrom(temp);
			}
			case DztFile dztFile -> {
				DztFile temp = new DztFile();
				temp.open(file);
				dztFile.loadFrom(temp);
			}
			case SonarFile sonarFile -> {
				SonarFile temp = new SonarFile();
				temp.open(file);
				sonarFile.loadFrom(temp);
			}
			default -> throw new IllegalArgumentException(
					"Unsupported file type: " + sgyFile.getClass().getSimpleName());
		}
		Platform.runLater(() -> model.reload(sgyFile));
	}
}

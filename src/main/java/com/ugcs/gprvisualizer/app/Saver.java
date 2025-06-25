package com.ugcs.gprvisualizer.app;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.Callable;

import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.auxcontrol.BaseObject;
import com.ugcs.gprvisualizer.event.FileRenameEvent;
import com.ugcs.gprvisualizer.event.WhatChanged;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import com.ugcs.gprvisualizer.utils.AuxElements;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileNames;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Range;
import com.ugcs.gprvisualizer.utils.Strings;
import com.ugcs.gprvisualizer.utils.Traces;
import javafx.event.ActionEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.ResourceImageHolder;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.ugcs.gprvisualizer.app.intf.Status;
import com.ugcs.gprvisualizer.draw.ToolProducer;
import com.ugcs.gprvisualizer.gpr.Model;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

@Component
public class Saver implements ToolProducer, InitializingBean {

	public static final String LAST_OPEN_FOLDER_SETTING_KEY = "last_open_folder";
	public static final String SAVER_SETTINGS_GROUP_KEY = "saver";

	private static final Logger log = LoggerFactory.getLogger(Saver.class);

	private final Button buttonOpen = ResourceImageHolder.setButtonImage(ResourceImageHolder.OPEN, new Button());
	private final Button buttonSave = ResourceImageHolder.setButtonImage(ResourceImageHolder.SAVE, new Button());
	private final Button buttonSaveTo = ResourceImageHolder.setButtonImage(ResourceImageHolder.SAVE_TO, new Button());

	@Autowired
	private Model model;
	
	@Autowired
	private Loader loader;
	
	@Autowired
	private Status status;

	@Autowired
	private PrefSettings prefSettings;

	public Saver(Model model) {
		this.model = model;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		buttonOpen.setTooltip(new Tooltip("Open files.."));
		buttonOpen.setOnAction(this::onOpen);

		buttonSave.setTooltip(new Tooltip("Save"));
		buttonSave.setOnAction(this::onSave);

		buttonSaveTo.setTooltip(new Tooltip("Save to.."));
		buttonSaveTo.setOnAction(this::onSaveTo);
	}

	@Override
	public List<Node> getToolNodes() {		
		return List.of(buttonOpen, buttonSave, buttonSaveTo);
	}

	private void onOpen(ActionEvent event) {
		List<File> fromFiles = selectFiles();
		if (!fromFiles.isEmpty()) {
			log.info("Opening files: {}", fromFiles);
			loader.load(fromFiles);
		}
	}

	private void onSave(ActionEvent event) {
		SgyFile selectedFile = model.getCurrentFile();
		if (selectedFile == null) {
			return;
		}

		File file = selectedFile.getFile();
		Check.notNull(file, "Path not specified");

		String actionName = "Saving " + file;
		if (selectedFile instanceof TraceFile traceFile) {
			runAction(actionName, () -> {
				saveGpr(traceFile);
				return null;
			});
		}
		if (selectedFile instanceof CsvFile csvFile) {
			runAction(actionName, () -> {
				saveCsv(csvFile);
				return null;
			});
		}
	}

	private void saveGpr(TraceFile traceFile) throws IOException {
		Check.notNull(traceFile);

		File file = traceFile.getFile();
		Check.notNull(file);

		log.info("Saving GPR meta {}", file);

		traceFile.saveMeta();
		traceFile.setUnsaved(false);

		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private void saveCsv(CsvFile csvFile) throws IOException {
		Check.notNull(csvFile);

		File file = csvFile.getFile();
		Check.notNull(file);

		log.info("Saving CSV {}", file);

		File folder = file.getParentFile();
		String extension = FileNames.getExtension(file.getName());

		// save to a temporary file
		File tmp = File.createTempFile("tmp", "." + extension, folder);
		csvFile.save(tmp);

		// delete current file
		boolean deleted = file.delete();
		if (!deleted) {
			log.error("Failed to delete file: {}", file);
		}

		// move tmp to a source path
		boolean renamed = tmp.renameTo(file);
		if (!renamed) {
			log.error("Failed to rename file: {} -> {}", tmp, file);
		}

		csvFile.setUnsaved(false);

		// Publish a file rename event to notify components
		// that the file has been renamed
		model.publishEvent(new FileRenameEvent(this, csvFile, file));
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private void onSaveTo(ActionEvent event) {
		SgyFile selectedFile = model.getCurrentFile();
		if (selectedFile == null) {
			return;
		}

		if (selectedFile instanceof TraceFile traceFile) {
			File toFolder = selectFolder(traceFile.getFile());
			if (toFolder != null) {
				String actionName = "Saving GPR to " + toFolder;
				runAction(actionName, () -> {
					saveToGpr(traceFile, toFolder);
					return null;
				});
			}
		}

		if (selectedFile instanceof CsvFile csvFile) {
			File toFile = selectFile(csvFile.getFile());
			if (toFile != null) {
				String actionName = "Saving CSV to " + toFile;
				runAction(actionName, () -> {
					saveToCsv(csvFile, toFile);
					return null;
				});
			}
		}
	}

	private void saveToGpr(TraceFile traceFile, File toFolder) throws IOException {
		Check.notNull(traceFile);
		Check.notNull(toFolder);

		File file = traceFile.getFile();
		Check.notNull(file);

		if (!toFolder.exists()) {
			boolean created = toFolder.mkdirs();
			if (!created) {
				throw new IOException("Could not create output directory: " + toFolder);
			}
		}

		log.info("Saving GPR lines to {}", toFolder);

		String baseName = FileNames.removeExtension(file.getName());
		String extension = FileNames.getExtension(file.getName());

		SortedMap<Integer, Range> lineRanges = traceFile.getLineRanges();
		int lineSequence = 1;

		for (Range range : lineRanges.values()) {
			String rangeFileName = String.format("%s_%03d.%s", baseName, lineSequence, extension);
			File rangeFile = new File(toFolder, rangeFileName);

			TraceFile rangeTraceFile = traceFile.copy(range);
			rangeTraceFile.setFile(rangeFile);
			rangeTraceFile.save(rangeFile);
			rangeTraceFile.saveAux(rangeFile);

			lineSequence++;
		}
	}

	private void saveToCsv(CsvFile csvFile, File toFile) throws IOException {
		Check.notNull(csvFile);
		Check.notNull(toFile);

		// check that target file is not open
		Optional<CsvFile> alreadyOpened = model.getFileManager().getCsvFiles().stream()
				.filter(f -> Objects.equals(f.getFile(), toFile))
				.findAny();

		if (alreadyOpened.isPresent()) {
			throw new IllegalArgumentException(
					"File in use. Close target file first: " + toFile);
		}

		log.info("Saving CSV to {}", toFile);

		File oldFile = csvFile.getFile();
		csvFile.save(toFile);
		csvFile.setUnsaved(false);

		model.updateCsvChartFile(csvFile, toFile);
		model.publishEvent(new FileRenameEvent(this, csvFile, oldFile));
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

	private List<File> selectFiles() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Open file");

		var lastOpenFolderPath = prefSettings.getSetting(
				SAVER_SETTINGS_GROUP_KEY,
				LAST_OPEN_FOLDER_SETTING_KEY);

		if (lastOpenFolderPath != null) {
			fileChooser.setInitialDirectory(new File(lastOpenFolderPath));
		}

		List<File> selectedFiles = Nulls.toEmpty(fileChooser
				.showOpenMultipleDialog(AppContext.stage));
		if (!selectedFiles.isEmpty()) {
			lastOpenFolderPath = selectedFiles.getFirst().getParentFile().getAbsolutePath();
			prefSettings.saveSetting(
					SAVER_SETTINGS_GROUP_KEY,
					Map.of(LAST_OPEN_FOLDER_SETTING_KEY, lastOpenFolderPath));
		}
		return selectedFiles;
	}

	private @Nullable File selectFile(@Nullable File initFile) {
		FileChooser fileChooser = new FileChooser();
		if (initFile != null) {
			fileChooser.setInitialFileName(initFile.getName());
			fileChooser.setInitialDirectory(initFile.getParentFile());
		}
		return fileChooser.showSaveDialog(AppContext.stage);
	}

	private @Nullable File selectFolder(@Nullable File initFile) {
		DirectoryChooser dirChooser = new DirectoryChooser();
		if (initFile != null) {
			dirChooser.setInitialDirectory(initFile.getParentFile());
		}
		return dirChooser.showDialog(AppContext.stage);
	}

	private void runAction(String actionName, Callable<Void> action) {
		ProgressTask task = listener -> {
			listener.progressMsg("Starting: "
					+ Strings.nullToEmpty(actionName));
			try {
				action.call();
				status.showProgressText("Completed: "
						+ Strings.nullToEmpty(actionName));
			} catch (Exception e) {
				MessageBoxHelper.showError(e.getMessage(), "");
			}
		};
		new TaskRunner(status, task).start();
	}
}

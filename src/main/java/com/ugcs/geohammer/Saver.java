package com.ugcs.geohammer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

import com.ugcs.geohammer.format.SgyFileWithMeta;
import com.ugcs.geohammer.format.svlog.SonarFile;
import com.ugcs.geohammer.model.ProgressTask;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileRenameEvent;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.service.TaskRunner;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.MessageBoxHelper;
import com.ugcs.geohammer.view.status.Status;
import javafx.event.ActionEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.ToolProducer;
import com.ugcs.geohammer.model.Model;

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
	private final Button buttonSaveAll = ResourceImageHolder.setButtonImage(ResourceImageHolder.SAVE_ALL, new Button());
	private final Button buttonCloseAll = ResourceImageHolder.setButtonImage(ResourceImageHolder.CLOSE_ALL, new Button());

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

		buttonSaveAll.setTooltip(new Tooltip("Save all"));
		buttonSaveAll.setOnAction(this::onSaveAll);

		buttonCloseAll.setTooltip(new Tooltip("Close all"));
		buttonCloseAll.setOnAction(this::onCloseAll);
	}

	@Override
	public List<Node> getToolNodes() {		
		return List.of(buttonOpen, buttonSave, buttonSaveTo, buttonSaveAll, buttonCloseAll);
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
        runAction(actionName, () -> {
            saveFile(selectedFile);
            return null;
        });
	}

    private void saveFile(SgyFile sgyFile) throws IOException {
        Check.notNull(sgyFile);

        File file = sgyFile.getFile();
        Check.notNull(file);

        log.info("Saving file {}", file);

        if (sgyFile instanceof SgyFileWithMeta sgyFileWithMeta) {
            sgyFileWithMeta.saveMeta();
        } else {
            sgyFile.save(file);
        }
        sgyFile.setUnsaved(false);
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

        if (selectedFile instanceof SonarFile sonarFile) {
            File toFile = selectFile(sonarFile.getFile());
            if (toFile != null) {
                String actionName = "Saving SVLOG to " + toFile;
                runAction(actionName, () -> {
                    saveToSonar(sonarFile, toFile);
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

		// TODO copy required only for denormalization
		//  of samples before save
		TraceFile copy = traceFile.copy();
		copy.denormalize();

		NavigableMap<Integer, IndexRange> lineRanges = traceFile.getLineRanges();
		int lineSequence = 1;
		for (IndexRange range : lineRanges.values()) {
			String rangeFileName = String.format("%s_%03d.%s", baseName, lineSequence, extension);
			File rangeFile = new File(toFolder, rangeFileName);

			copy.save(rangeFile, range);

			lineSequence++;
		}
	}

    private void checkNotOpened(File file) {
        if (file == null) {
            return;
        }
        Optional<SgyFile> alreadyOpened = model.getFileManager().getFiles().stream()
                .filter(f -> Objects.equals(f.getFile(), file))
                .findAny();
        if (alreadyOpened.isPresent()) {
            throw new IllegalArgumentException(
                    "File in use. Close target file first: " + file);
        }
    }

	private void saveToCsv(CsvFile csvFile, File toFile) throws IOException {
		Check.notNull(csvFile);
		Check.notNull(toFile);

		// check that target file is not open
        checkNotOpened(toFile);

		log.info("Saving CSV to {}", toFile);

		File oldFile = csvFile.getFile();
		csvFile.save(toFile);
		csvFile.setUnsaved(false);

		model.updateChartFile(csvFile, toFile);
		model.publishEvent(new FileRenameEvent(this, csvFile, oldFile));
		model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
	}

    private void saveToSonar(SonarFile sonarFile, File toFile) throws IOException {
        Check.notNull(sonarFile);
        Check.notNull(toFile);

        // check that target file is not open
        checkNotOpened(toFile);

        log.info("Saving SVLOG to {}", toFile);
        sonarFile.save(toFile);
    }

	private List<File> selectFiles() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Open file");

		var lastOpenFolderPath = prefSettings.getSetting(
				SAVER_SETTINGS_GROUP_KEY,
				LAST_OPEN_FOLDER_SETTING_KEY);

		if (lastOpenFolderPath != null) {
			File file = new File(lastOpenFolderPath);
			if (file.exists() && file.isDirectory()) {
				fileChooser.setInitialDirectory(file);
			} else {
				log.warn("Last open folder does not exist: {}", lastOpenFolderPath);
			}
		}

		List<File> selectedFiles = Nulls.toEmpty(fileChooser
				.showOpenMultipleDialog(AppContext.stage));
		if (!selectedFiles.isEmpty()) {
			lastOpenFolderPath = selectedFiles.getFirst().getParentFile().getAbsolutePath();
			prefSettings.saveSetting(
					SAVER_SETTINGS_GROUP_KEY,
					LAST_OPEN_FOLDER_SETTING_KEY,
					lastOpenFolderPath);
		}
		return selectedFiles;
	}

	private @Nullable File selectFile(@Nullable File initFile) {
		FileChooser fileChooser = new FileChooser();
		if (initFile != null) {
			fileChooser.setInitialFileName(initFile.getName());
			fileChooser.setInitialDirectory(initFile.getParentFile());
			if (FileTypes.isCsvFile(initFile)) {
				fileChooser.getExtensionFilters().add(
						new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv")
				);
			}
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

	private void onSaveAll(ActionEvent event) {
		List<SgyFile> files = model.getFileManager().getFiles();
		if (files.isEmpty()) {
			return;
		}

		String actionName = "Saving all opened files";
		runAction(actionName, () -> {
			for (SgyFile file : files) {
                saveFile(file);
			}
			return null;
		});
	}

	private void onCloseAll(ActionEvent event) {
		List<SgyFile> files = model.getFileManager().getFiles();
		if (files.isEmpty()) {
			return;
		}
		String actionName = "Closing all opened files";
		runAction(actionName, () -> {
			if (model.stopUnsaved()) {
				return null;
			}
			// Make a copy to avoid concurrent modification
			for (SgyFile file : files.toArray(new SgyFile[0])) {
				model.publishEvent(new FileClosedEvent(this, file));
			}
			return null;
		});
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
				log.error("Error", e);
				MessageBoxHelper.showError(e.getMessage(), "");
			}
		};
		new TaskRunner(status, task).start();
	}
}

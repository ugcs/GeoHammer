package com.ugcs.gprvisualizer.app.ext;

import com.github.thecoldwine.sigrun.common.ext.*;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.dzt.DztFile;
import com.ugcs.gprvisualizer.event.FileOpenErrorEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class FileManager {

	private static final Logger log = LoggerFactory.getLogger(FileManager.class.getName());

	private static final FilenameFilter SGY = (dir, name) -> name.toLowerCase().endsWith(".sgy");

	//public boolean levelCalculated = false;

	// Do not hash-based collection here (like HashSet)
	// as files may be renamed and thus hashes would be invalidated
	private final List<SgyFile> files = new ArrayList<>();

	@Nullable
	private File topFolder = null;

	private final FileTemplates fileTemplates;

    private final ApplicationEventPublisher eventPublisher;

	FileManager(FileTemplates fileTemplates, ApplicationEventPublisher eventPublisher) {
		this.fileTemplates = fileTemplates;
        this.eventPublisher = eventPublisher;
	}

	public boolean isActive() {
		return files != null && files.size() > 0;
	}

	public void processList(List<File> fileList, ProgressListener listener) throws Exception {
		Set<File> sf = new TreeSet<>(fileList);

		for (File fl : sf) {
			if (fl.isDirectory()) {
				processDirectory(fl, listener);
			} else {
				listener.progressMsg("load file " + fl.getAbsolutePath());
				processFile(fl);
			}
		}
	}

	public void clear() {
		//levelCalculated = false;
		files.clear(); // = new ArrayList<>();
		topFolder = null;
	}

	private void processDirectory(File fl, ProgressListener listener) throws Exception {
		if (topFolder == null) {
			topFolder = fl;
		}

		listener.progressMsg("load directory " + fl.getAbsolutePath());

		processFileList(Arrays.asList(fl.listFiles(SGY)));
	}

	private void processFile(File fl) throws Exception {
		TraceFile sgyFile = null;
		if (fl.getName().toLowerCase().endsWith("sgy")) {
			sgyFile = new GprFile();
		} else if (fl.getName().toLowerCase().endsWith("dzt")) {
			sgyFile = new DztFile();
		} 
		
		if (sgyFile == null) {
			return;
		}

		try {
            sgyFile.open(fl);

            files.add(sgyFile);

            try {
                new  PositionFile(fileTemplates).load(sgyFile);
            } catch (Exception e) {
                log.warn("Error loading markup or position files: {}", e.getMessage());
            }
        } catch (Exception e) {
            eventPublisher.publishEvent(new FileOpenErrorEvent(this, fl, e));
            throw e;
        }
	}

	private void processFileList(List<File> fileList) throws Exception {
		for (File fl : fileList) {
			processFile(fl);
		}
	}

	public boolean isUnsavedExists() {
		if (isActive()) {
			for (SgyFile sgyFile : files) {
				if (sgyFile.isUnsaved()) {
					return true;
				}
			}
		}

		return false;
	}

	public FileTemplates getFileTemplates() {
		return fileTemplates;
	}

	public void addFile(SgyFile sgyFile) {
		files.add(sgyFile);
	}

	public void removeFile(SgyFile sgyFile) {
		files.remove(sgyFile);
	}

	public List<TraceFile> getGprFiles() {
		return files.stream()
				.filter(TraceFile.class::isInstance)
				.map(TraceFile.class::cast)
				.collect(Collectors.toList());
	}

	public List<CsvFile> getCsvFiles() {
		return files.stream()
				.filter(CsvFile.class::isInstance)
				.map(CsvFile.class::cast)
				.collect(Collectors.toList());
	}

	public List<SgyFile> getFiles() {
		return Collections.unmodifiableList(files);
	}

	public int getFilesCount() {
		return files.size();
	}

    public void updateFiles(List<SgyFile> slicedSgyFiles) {
		clear();
		files.addAll(slicedSgyFiles);
    }
}

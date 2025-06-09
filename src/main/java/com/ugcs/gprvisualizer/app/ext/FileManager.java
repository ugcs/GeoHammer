package com.ugcs.gprvisualizer.app.ext;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.PositionFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.dzt.DztFile;

@Component
public class FileManager {

	private static final Logger log = LoggerFactory.getLogger(FileManager.class.getName());

	private static final FilenameFilter SGY = (dir, name) -> name.toLowerCase().endsWith(".sgy");

	//public boolean levelCalculated = false;
	
	private final List<SgyFile> files = new ArrayList<>();

	@Nullable
	private File topFolder = null;

	private final FileTemplates fileTemplates;

	FileManager(FileTemplates fileTemplates) {
		this.fileTemplates = fileTemplates;
	}

	public boolean isActive() {
		return files != null && files.size() > 0;
	}

	public void processList(List<File> fileList, ProgressListener listener) throws Exception {
		//clear();

		Set<File> sf = new TreeSet<File>(fileList);

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
		clearTraces();
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
		
		sgyFile.open(fl);
		
		files.add(sgyFile);

		try {	
			new PositionFile(fileTemplates).load(sgyFile);
		} catch (Exception e) {
			log.warn("Error loading markup or position files: {}", e.getMessage());
		}
	}

	private void processFileList(List<File> fileList) throws Exception {
		for (File fl : fileList) {
			processFile(fl);
		}
	}

	private List<Trace> gprTraces = new ArrayList<>();

	public List<Trace> getGprTraces() {
		if (gprTraces.isEmpty()) {
			for (SgyFile file : files) {
				if (file instanceof TraceFile traceFile) {
					for (Trace trace : traceFile.getTraces()) {
						gprTraces.add(trace);
					}
				}
			}
		}
		return gprTraces;
	}

	public void clearTraces() {
		gprTraces.clear();
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
		if (sgyFile instanceof TraceFile) {
			gprTraces.clear();
		}
	}

	public void removeFile(SgyFile sgyFile) {
		boolean removed = files.remove(sgyFile);
		if (removed && sgyFile instanceof TraceFile) {
			gprTraces.clear();
		}
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

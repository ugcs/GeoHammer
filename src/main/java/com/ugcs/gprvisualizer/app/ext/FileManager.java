package com.ugcs.gprvisualizer.app.ext;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.GprFile;
import com.github.thecoldwine.sigrun.common.ext.MarkupFile;
import com.github.thecoldwine.sigrun.common.ext.PositionFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.Trace;
import com.github.thecoldwine.sigrun.converters.ByteANumberConverter;
import com.ugcs.gprvisualizer.app.Broadcast;
import com.ugcs.gprvisualizer.app.ProgressListener;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.dzt.DztFile;

@Component
public class FileManager {

	private static final Logger log = LoggerFactory.getLogger(FileManager.class.getName());

	private static final FilenameFilter SGY = (dir, name) -> name.toLowerCase().endsWith(".sgy");

	//public boolean levelCalculated = false;
	
	private final List<SgyFile> files = new ArrayList<>();

	private File topFolder = null;

	private final FileTemplates fileTemplates;

	//@Autowired
	//private Broadcast broadcast;

	FileManager(FileTemplates fileTemplates) { //, Broadcast broadcast) {
		this.fileTemplates = fileTemplates;
		//this.broadcast = broadcast;
	}

	public boolean isActive() {
		return files != null && !files.isEmpty();
	}

	public void processList(List<File> fileList, ProgressListener listener) throws Exception {
		clear();

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
		
		SgyFile sgyFile = null;
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
			new MarkupFile().load(sgyFile);
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

	/*public List<SgyFile> getFiles() {
		return files;
	}*/


	/*public void setFiles(List<SgyFile> fl) {
		clear();
		files = fl;
	}*/

	private List<Trace> gprTraces = new ArrayList<>();

	public List<Trace> getGprTraces() {
		if (gprTraces.isEmpty()) {
			int traceIndex = 0;
			for (SgyFile file : files) {
				if (file instanceof CsvFile) {
					continue;
				}
				for (Trace trace : file.getTraces()) {
					gprTraces.add(trace);
					trace.setIndexInSet(traceIndex++);
				}
			}
		}
		return gprTraces;
	}

	private  List<Trace> csvTraces = new ArrayList<>();

	public List<Trace> getCsvTraces() {
		if (csvTraces.isEmpty()) {
			int traceIndex = 0;
			for (SgyFile file : files) {
				if (!(file instanceof CsvFile)) {
					continue;
				}
				for (Trace trace : file.getTraces()) {
					csvTraces.add(trace);
					trace.setIndexInSet(traceIndex++);
				}
			}
		}
		return csvTraces;
	}

	public void clearTraces() {
		gprTraces.clear();
		csvTraces.clear();
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
		if (sgyFile instanceof CsvFile) {
			csvTraces.clear();
		} else {
			gprTraces.clear();		
		}
	}

	public void removeFile(SgyFile sgyFile) {
		boolean removed = files.remove(sgyFile);
		if (removed && sgyFile instanceof CsvFile) {
			csvTraces.clear();
		} else if (removed) {
			gprTraces.clear();		
		}
	}

	public List<SgyFile> getGprFiles() {
		return files.stream().filter(Predicate.not(CsvFile.class::isInstance)).collect(Collectors.toList());
	}

	public List<SgyFile> getCsvFiles() {
		return files.stream().filter(CsvFile.class::isInstance).collect(Collectors.toList());
	}

	public int getFilesCount() {
		return files.size();
	}

    public void updateFiles(List<SgyFile> slicedSgyFiles) {
		clear();
		files.addAll(slicedSgyFiles);
    }

}

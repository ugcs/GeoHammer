package com.ugcs.gprvisualizer.app.ext;

import com.github.thecoldwine.sigrun.common.ext.*;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class FileManager {

	// Do not hash-based collection here (like HashSet)
	// as files may be renamed and thus hashes would be invalidated
	private final List<SgyFile> files = new ArrayList<>();

	private final FileTemplates fileTemplates;

	FileManager(FileTemplates fileTemplates) {
		this.fileTemplates = fileTemplates;
	}

	public boolean isActive() {
		return files != null && files.size() > 0;
	}

	public void clear() {
		files.clear();
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

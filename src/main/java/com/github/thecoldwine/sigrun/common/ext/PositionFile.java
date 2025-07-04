package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

import com.ugcs.gprvisualizer.utils.FileTypes;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.csv.CSVParsersFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import org.jspecify.annotations.Nullable;

public class PositionFile {

	private FileTemplates fileTemplates;

	@Nullable
	private File positionFile;

	public PositionFile(FileTemplates fileTemplates) {
		this.fileTemplates = fileTemplates;
	}

	public void load(TraceFile traceFile) throws Exception {
		var positionFile = getPositionFileBySgy(traceFile.getFile());
		if (positionFile.isPresent()) {
			load(traceFile, positionFile.get());
		} else {
			System.out.println("Position file not found for " + traceFile.getFile().getAbsolutePath());
		}
	}

	private void load(TraceFile traceFile, File positionFile) throws FileNotFoundException {
		this.positionFile = positionFile;

		String logPath = positionFile.getAbsolutePath();
		var fileTemplate = fileTemplates.findTemplate(fileTemplates.getTemplates(), logPath);

		if (fileTemplate == null) {
			throw new RuntimeException("Can`t find template for file " + positionFile.getName());
		}

		System.out.println("template: " + fileTemplate.getName());
		CsvParser parser = new CSVParsersFactory().createCSVParser(fileTemplate);

		List<GeoCoordinates> coordinates = parser.parse(logPath);

		HorizontalProfile hp = new HorizontalProfile(traceFile.numTraces());
		StretchArray altArr = new StretchArray();

		double hair = 100 / traceFile.getSamplesToCmAir();

		for (GeoCoordinates coord : coordinates) {
			if (coord instanceof GeoData && ((GeoData) coord).getSensorValue(GeoData.Semantic.ALTITUDE_AGL).data() != null) {
				double alt = ((GeoData) coord).getSensorValue(GeoData.Semantic.ALTITUDE_AGL).data().doubleValue();
				altArr.add((int) (alt * hair));
			}
		}

		hp.deep = altArr.stretchToArray(traceFile.getTraces().size());

		hp.finish(traceFile.getTraces());
		hp.color = Color.red;

		traceFile.setGroundProfile(hp);
		traceFile.setGroundProfileSource(this);
	}

	private Optional<File> getPositionFileBySgy(File file) {
		if (!FileTypes.isGprFile(file)) {
			return Optional.empty();
		}
		// lookup position files in a parent directory
		File parent = file.getParentFile();
		if (parent == null) {
			return Optional.empty();
		}

		File[] positionFiles = parent.listFiles((dir, fileName)
				-> FileTypes.isPositionFile(fileName));
		if (positionFiles == null) {
			return Optional.empty();
		}

		// match position files with current file by name
		for (File positionFile : positionFiles) {
			if (FileTypes.isPositionFileFor(positionFile, file)) {
				return Optional.of(positionFile);
			}
		}
		return Optional.empty();
	}

	@Nullable
	public File getPositionFile() {
		return positionFile;
	}
}

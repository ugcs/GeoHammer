package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Optional;

import com.ugcs.gprvisualizer.app.parcers.SensorValue;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileTypes;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.csv.CSVParsersFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.utils.Nulls;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionFile {

	private static final Logger log = LoggerFactory.getLogger(PositionFile.class);

	private FileTemplates templates;

	@Nullable
	private File positionFile;

	public PositionFile(FileTemplates templates) {
		this.templates = templates;
	}

	@Nullable
	public File getPositionFile() {
		return positionFile;
	}

	private Optional<File> getPositionFileFor(File file) {
		if (file == null) {
			return Optional.empty();
		}
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

	public void load(TraceFile traceFile) throws Exception {
		Check.notNull(traceFile);

		var positionFile = getPositionFileFor(traceFile.getFile());
		if (positionFile.isPresent()) {
			load(traceFile, positionFile.get());
		} else {
			log.info("No position file found for {}", traceFile.getFile());
		}
	}

	private void load(TraceFile traceFile, File positionFile) throws FileNotFoundException {
		Check.notNull(traceFile);
		Check.notNull(positionFile);

		List<GeoCoordinates> coordinates = parsePositionFile(positionFile);

		// sample distance in cm
		double sampleDistance = traceFile.getSamplesToCmAir();
		// num samples in a meter
		double samplesPerMeter = 100.0 / sampleDistance;

		StretchArray altitudes = toAltitudesInSamples(coordinates, samplesPerMeter);

		// create horizontal profile
		int numGlobalTraces = traceFile.traces.size();
		HorizontalProfile profile = new HorizontalProfile(
				altitudes.stretchToArray(numGlobalTraces),
				traceFile.getMetaFile());
		profile.setColor(Color.red);

		this.positionFile = positionFile;
		traceFile.setGroundProfileSource(this);
		traceFile.setGroundProfile(profile);
	}

	private List<GeoCoordinates> parsePositionFile(File file) throws FileNotFoundException {
		Check.notNull(file);

		String path = file.getAbsolutePath();
		var template = templates.findTemplate(templates.getTemplates(), path);
		if (template == null) {
			throw new RuntimeException("Can`t find template for file " + file.getName());
		}

		log.info("Using position file template: {}", template.getName());
		CsvParser parser = new CSVParsersFactory().createCSVParser(template);
        return parser.parse(path);
	}

	private StretchArray toAltitudesInSamples(List<GeoCoordinates> coordinates, double samplesPerMeter) {
		StretchArray altitudes = new StretchArray();

		for (GeoCoordinates c : Nulls.toEmpty(coordinates)) {
			if (c instanceof GeoData geoData) {
				SensorValue altitudeAgl = geoData.getSensorValue(GeoData.Semantic.ALTITUDE_AGL);
				if (altitudeAgl != null && altitudeAgl.data() != null) {
					double altitudeSamples = samplesPerMeter * altitudeAgl.data().doubleValue();
					altitudes.add((int)altitudeSamples);
				}
			}
		}

		return altitudes;
	}
}

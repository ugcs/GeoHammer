package com.github.thecoldwine.sigrun.common.ext;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.gpr.PrefSettings;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileTypes;

import com.ugcs.gprvisualizer.app.parcers.GeoCoordinates;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.csv.CSVParsersFactory;
import com.ugcs.gprvisualizer.app.parcers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.math.HorizontalProfile;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionFile {

	private static final Logger log = LoggerFactory.getLogger(PositionFile.class);

	private final PrefSettings prefSettings;

	private FileTemplates templates;

	@Nullable
	private File positionFile;

	private Template template;

	private List<GeoCoordinates> coordinates;

	public PositionFile(FileTemplates templates, PrefSettings prefSettings) {
		this.templates = templates;
		this.prefSettings = prefSettings;
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

		this.coordinates = parsePositionFile(positionFile);
		this.positionFile = positionFile;

		traceFile.setGroundProfileSource(this);
		List<String> traceSemantics = getAvailableTraceSemantics();
		if (!traceSemantics.isEmpty()) {
			setGroundProfile(traceFile, traceSemantics.getFirst());
		}
	}

	private List<GeoCoordinates> parsePositionFile(File file) throws FileNotFoundException {
		Check.notNull(file);

		String path = file.getAbsolutePath();
		template = templates.findTemplate(templates.getTemplates(), path);
		if (template == null) {
			throw new RuntimeException("Can`t find template for file " + file.getName());
		}

		log.info("Using position file template: {}", template.getName());
		CsvParser parser = new CSVParsersFactory().createCSVParser(template, prefSettings);
        return parser.parse(path);
	}

	private HorizontalProfile loadGroundProfile(TraceFile traceFile, String traceSemantic) {
		Check.notNull(traceFile);

		if (Strings.isNullOrEmpty(traceSemantic)) {
			return null;
		}

		// sample distance in cm
		double sampleDistance = traceFile.getSamplesToCmAir();
		// num samples in a meter
		double samplesPerMeter = 100.0 / sampleDistance;

		// total number of traces
		int numTraces = traceFile.traces.size();
		int[] depths = new int[numTraces];

		for (GeoCoordinates c : Nulls.toEmpty(coordinates)) {
			if (c instanceof GeoData geoData) {
				Integer traceIndex = geoData.getInt(traceSemantic).orElse(null);
				if (traceIndex == null || traceIndex < 0 || traceIndex >= numTraces) {
					continue;
				}
				Optional<Double> altitudeAgl = geoData
						.getDouble(GeoData.Semantic.ALTITUDE_AGL.getName());
				if (altitudeAgl.isPresent()) {
					double altitudeSamples = samplesPerMeter * altitudeAgl.get();
					depths[traceIndex] = (int)altitudeSamples;
				}
			}
		}

		// create horizontal profile
		HorizontalProfile profile = new HorizontalProfile(
				depths,
				traceFile.getMetaFile());
		profile.setColor(Color.red);
		return profile;
	}

	public void setGroundProfile(TraceFile traceFile, String traceSemantic) {
		Check.notNull(traceFile);

		HorizontalProfile profile = loadGroundProfile(traceFile, traceSemantic);

		traceFile.setGroundProfileSource(this);
		traceFile.setGroundProfileTraceSemantic(Strings.emptyToNull(traceSemantic));
		traceFile.setGroundProfile(profile);
	}

	public List<String> getAvailableTraceSemantics() {
		if (template == null) {
			return List.of();
		}
		DataMapping mapping = template.getDataMapping();
		if (mapping == null) {
			return List.of();
		}

		Set<String> traceHeaders = new HashSet<>();
		for (BaseData sgyTrace : Nulls.toEmpty(mapping.getSgyTraces())) {
			traceHeaders.add(Strings.nullToEmpty(sgyTrace.getHeader()));
		}
		List<String> traceSemantics = new ArrayList<>();
		for (SensorData sensorData : Nulls.toEmpty(mapping.getDataValues())) {
			if (sensorData.getIndex() == -1) {
				continue; // no header in the data file
			}
			if (traceHeaders.contains(sensorData.getHeader())) {
				traceSemantics.add(sensorData.getSemantic());
			}
		}
		return traceSemantics;
	}
}

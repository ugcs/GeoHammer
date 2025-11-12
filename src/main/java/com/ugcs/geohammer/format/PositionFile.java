package com.ugcs.geohammer.format;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ugcs.geohammer.format.csv.parser.Parser;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileTypes;

import com.ugcs.geohammer.format.csv.parser.ParserFactory;
import com.ugcs.geohammer.format.csv.parser.CsvParser;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionFile {

	private static final Logger log = LoggerFactory.getLogger(PositionFile.class);

	private FileTemplates templates;

	@Nullable
	private Parser parser;

	@Nullable
	private File positionFile;

	private List<GeoData> geoData;

	public PositionFile(FileTemplates templates) {
		this.templates = templates;
	}

	@Nullable
	public Template getTemplate() {
		return parser != null ? parser.getTemplate() : null;
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

	private void load(TraceFile traceFile, File positionFile) throws IOException {
		Check.notNull(traceFile);
		Check.notNull(positionFile);

		this.geoData = parsePositionFile(positionFile);
		this.positionFile = positionFile;

		traceFile.setGroundProfileSource(this);
		List<String> traceHeaders = getAvailableTraceHeaders();
		if (!traceHeaders.isEmpty()) {
			setGroundProfile(traceFile, traceHeaders.getFirst());
		}
	}

	private List<GeoData> parsePositionFile(File file) throws IOException {
		Check.notNull(file);

		Template template = templates.findTemplate(templates.getTemplates(), file);
		if (template == null) {
			throw new RuntimeException("Can`t find template for file " + file.getName());
		}

		log.info("Using position file template: {}", template.getName());
		parser = ParserFactory.createParser(template);
        return parser.parse(file);
	}

	private HorizontalProfile loadGroundProfile(TraceFile traceFile, String traceHeader) {
		Check.notNull(traceFile);

		if (Strings.isNullOrEmpty(traceHeader)) {
			return null;
		}

		// sample distance in cm
		double sampleDistance = traceFile.getSamplesToCmAir();
		// num samples in a meter
		double samplesPerMeter = 100.0 / sampleDistance;

		// total number of traces
		int numTraces = traceFile.traces.size();
		int[] depths = new int[numTraces];

		for (GeoData value : Nulls.toEmpty(geoData)) {
			Number traceIndex = value.getNumber(traceHeader);
			if (traceIndex == null || traceIndex.intValue() < 0 || traceIndex.intValue() >= numTraces) {
				continue;
			}
			Number altitudeAgl = value.getNumberBySemantic(Semantic.ALTITUDE_AGL.getName());
			if (altitudeAgl != null) {
				double altitudeSamples = samplesPerMeter * altitudeAgl.doubleValue();
				depths[traceIndex.intValue()] = (int)altitudeSamples;
			}
		}

		// create horizontal profile
		HorizontalProfile profile = new HorizontalProfile(
				depths,
				traceFile.getMetaFile());
		profile.setColor(Color.red);
		return profile;
	}

	public void setGroundProfile(TraceFile traceFile, String traceHeader) {
		Check.notNull(traceFile);

		HorizontalProfile profile = loadGroundProfile(traceFile, traceHeader);

		traceFile.setGroundProfileSource(this);
		traceFile.setGroundProfileTraceHeader(Strings.emptyToNull(traceHeader));
		traceFile.setGroundProfile(profile);
	}

	public List<String> getAvailableTraceHeaders() {
		Template template = getTemplate();
		if (template == null) {
			return List.of();
		}
		DataMapping mapping = template.getDataMapping();
		if (mapping == null) {
			return List.of();
		}

		List<String> traceHeaders = new ArrayList<>();
		for (BaseData sgyTrace : Nulls.toEmpty(mapping.getSgyTraces())) {
			String traceHeader = sgyTrace.getHeader();
			if (Strings.isNullOrEmpty(traceHeader)) {
				continue;
			}
			if (parser != null && parser.hasHeader(traceHeader)) {
				traceHeaders.add(traceHeader);
			}
		}
		return traceHeaders;
	}
}

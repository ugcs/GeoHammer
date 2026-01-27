package com.ugcs.geohammer.format;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.ugcs.geohammer.format.csv.parser.Parser;
import com.ugcs.geohammer.math.LinearInterpolator;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileTypes;

import com.ugcs.geohammer.format.csv.parser.ParserFactory;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionFile {

	private static final Logger log = LoggerFactory.getLogger(PositionFile.class);

	private final FileTemplates templates;

	@Nullable
	private Parser parser;

	@Nullable
	private File file;

	private List<GeoData> geoData;

    @Nullable
    private String traceHeader;

    @Nullable
    private String altitudeHeader;

    @Nullable
    private String ellipsoidalHeightHeader;

	public PositionFile(FileTemplates templates) {
		this.templates = templates;
	}

	@Nullable
	public Template getTemplate() {
		return parser != null ? parser.getTemplate() : null;
	}

	@Nullable
	public File getFile() {
		return file;
	}

    public List<GeoData> getGeoData() {
        return geoData;
    }

    public @Nullable String getTraceHeader() {
        return traceHeader;
    }

    public void setTraceHeader(@Nullable String traceHeader) {
        this.traceHeader = traceHeader;
    }

    public @Nullable String getAltitudeHeader() {
        return altitudeHeader;
    }

    public void setAltitudeHeader(@Nullable String altitudeHeader) {
        this.altitudeHeader = altitudeHeader;
    }

    public @Nullable String getEllipsoidalHeightHeader() {
        return ellipsoidalHeightHeader;
    }

    public void setEllipsoidalHeightHeader(@Nullable String ellipsoidalHeightHeader) {
        this.ellipsoidalHeightHeader = ellipsoidalHeightHeader;
    }

    public static Optional<File> findFor(TraceFile traceFile) {
        if (traceFile == null) {
            return Optional.empty();
        }
        File file = traceFile.getFile();
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

	public void load(File file) throws IOException {
		Check.notNull(file);

        this.file = file;
		this.geoData = parseFile(file);
        this.traceHeader = getDefaultTraceHeader();
        this.altitudeHeader = getDefaultAltitudeHeader();
        this.ellipsoidalHeightHeader = getDefaultEllipsoidalHeightHeader();
	}

	private List<GeoData> parseFile(File file) throws IOException {
		Check.notNull(file);

		Template template = templates.findTemplate(file);
		if (template == null) {
			throw new RuntimeException("Can`t find template for file " + file.getName());
		}

		log.info("Using position file template: {}", template.getName());
		parser = ParserFactory.createParser(template);
        return parser.parse(file);
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

    public String getDefaultTraceHeader() {
        List<String> availableHeaders = getAvailableTraceHeaders();
        if (!availableHeaders.isEmpty()) {
            return availableHeaders.getFirst();
        }
        return null;
    }

    public List<String> getAvailableAltitudeHeaders() {
        ColumnSchema schema = GeoData.getSchema(geoData);
        if (schema == null) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(schema.getDisplayHeaders());
        headers.sort(Comparator.naturalOrder());
        return headers;
    }

    public String getDefaultAltitudeHeader() {
        String altitudeHeader = null;
        ColumnSchema schema = GeoData.getSchema(geoData);
        if (schema != null) {
            altitudeHeader = schema.getHeaderBySemantic(Semantic.ALTITUDE_AGL.getName());
            if (Strings.isNullOrEmpty(altitudeHeader)) {
                List<String> availableHeaders = getAvailableAltitudeHeaders();
                if (!availableHeaders.isEmpty()) {
                    altitudeHeader = availableHeaders.getFirst();
                }
            }
        }
        return altitudeHeader;
    }

    public List<String> getAvailableEllipsoidalHeightHeaders() {
        ColumnSchema schema = GeoData.getSchema(geoData);
        if (schema == null) {
            return List.of();
        }
        List<String> headers = new ArrayList<>(schema.getDisplayHeaders());
        headers.sort(Comparator.naturalOrder());
        headers.addFirst(Strings.empty());
        return headers;
    }

    public String getDefaultEllipsoidalHeightHeader() {
        String ellipsoidalHeightHeader = null;
        ColumnSchema schema = GeoData.getSchema(geoData);
        if (schema != null) {
            ellipsoidalHeightHeader = schema.getHeaderBySemantic(Semantic.ALTITUDE.getName());
            if (Strings.isNullOrEmpty(ellipsoidalHeightHeader)) {
                ellipsoidalHeightHeader = Strings.empty();
            }
        }
        return ellipsoidalHeightHeader;
    }

    public double[] traceValues(TraceFile traceFile, String header) {
        if (Strings.isNullOrEmpty(traceHeader) || Strings.isNullOrEmpty(header)) {
            return new double[0];
        }

        int numTraces = traceFile.traces.size();
        double[] values = new double[numTraces];
        Arrays.fill(values, Double.NaN);

        for (GeoData value : Nulls.toEmpty(geoData)) {
            Number traceIndex = value.getNumber(traceHeader);
            if (traceIndex == null || traceIndex.intValue() < 0 || traceIndex.intValue() >= numTraces) {
                continue;
            }
            Number number = value.getNumber(header);
            if (number != null) {
                values[traceIndex.intValue()] = number.doubleValue();
            }
        }
        // interpolate missing values
        LinearInterpolator.interpolateNans(values);
        // set remaining nans to zeros
        for (int i = 0; i < values.length; i++) {
            if (Double.isNaN(values[i])) {
                values[i] = 0;
            }
        }
        return values;
    }
}

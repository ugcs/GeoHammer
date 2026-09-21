package com.ugcs.geohammer.format.csv.parser;

import com.google.re2j.Pattern;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.Column;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Semantic;
import com.ugcs.geohammer.model.template.DataMapping;
import com.ugcs.geohammer.model.template.SkipLinesTo;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.model.template.data.Date;
import com.ugcs.geohammer.model.template.data.DateTime;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.GpsTime;
import com.ugcs.geohammer.util.IncorrectFormatException;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Numbers;
import com.ugcs.geohammer.util.PrintableFilter;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

public abstract class Parser {

    protected final Template template;

    // contains lines that were skipped during parsing
    protected List<String> skippedLines = List.of();

    // header -> index in a file headers line
    protected Map<String, Integer> headers = Map.of();

    // date and time parsed from the filename
    protected LocalDate dateFromFilename;

	private final Warnings warnings = new Warnings();

    public Parser(Template template) {
        this.template = Check.notNull(template);
    }

    public Template getTemplate() {
        return template;
    }

    public List<String> getSkippedLines() {
        return skippedLines;
    }

    public List<String> getHeaders() {
        return new ArrayList<>(Nulls.toEmpty(headers).keySet());
    }

    public void setHeaders(List<String> headers) {
        this.headers = new LinkedHashMap<>(headers.size());
        Nulls.toEmpty(headers).forEach(header
                -> this.headers.put(Objects.requireNonNull(header), this.headers.size()));
    }

    public boolean hasHeader(String header) {
        return headers.containsKey(header);
    }

    private boolean hasHeader(BaseData column) {
        return column != null && headers.containsKey(column.getHeader());
    }

	public Warnings getWarnings() {
		return warnings;
	}

	public List<GeoData> parse(File file) throws IOException {
        Check.notNull(file);

        try {
            // set date from filename
            dateFromFilename = null;
            Date dateColumn = template.getDataMapping().getDate();
            if (dateColumn != null && dateColumn.getSource() == Date.Source.FileName) {
                dateFromFilename = parseDateFromFilename(file.getName());
            }

            // parse data lines
            List<GeoData> values = parseFile(file);

            // timestamps could be in wrong order in the file
            if (template.isReorderByTime()) {
                values.sort(Comparator.comparing(
                        GeoData::getDateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
            }

            return values;
        } catch (IOException | CancellationException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse " + file.getName()
                    + " with template '" + template.getName() + "': "
                    + Strings.nullToEmpty(e.getMessage()), e);
        }
    }

    private List<GeoData> parseFile(File file) throws IOException {
        Check.notNull(file);

        DataMapping mapping = template.getDataMapping();

        ColumnSchema valueSchema;
        List<GeoData> values = new ArrayList<>();
		FileSchema fileSchema;
        PrintableFilter filter = new PrintableFilter(new FileReader(file));
        try (BufferedReader r = new BufferedReader(filter)) {
            // skip top lines
            skipLines(r);

            // read headers
            String[] headerTokens = readHeaders(r);
            Check.notNull(headerTokens, "No header found");

            setHeaders(Arrays.asList(headerTokens));
            if (!hasHeader(mapping.getLatitude()) || !hasHeader(mapping.getLongitude())) {
                throw new ParseException("Column names for latitude and longitude are not matched");
            }

            valueSchema = buildValueSchema();
			fileSchema = buildFileSchema(valueSchema);

            // read value lines
            String[] valueTokens;
            while ((valueTokens = readValues(r)) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException();
                }
                GeoData value = parseValues(valueTokens, fileSchema, valueSchema);
                values.add(value);
            }

			if (values.isEmpty()) {
				throw new ParseException("File has no data.");
			}

			checkCoordinates(values, fileSchema);
        }

		// decide which columns to display
        // based on loaded data values
        setColumnDisplay(fileSchema, valueSchema);

        // add rejected symbols warning
        if (filter.numRejected() > 0) {
            warnings.addWarning("The file contains non-printable characters."
                    + " They were ignored while reading and will be lost if you save the file.");
        }

        return values;
    }

	private void checkCoordinates(List<GeoData> values, FileSchema fileSchema) throws ParseException {
		for (GeoData value : values) {
			Number latitude = value.getNumber(fileSchema.latitudeColumn.valueIndex);
			Number longitude = value.getNumber(fileSchema.longitudeColumn.valueIndex);
			if (latitude != null && longitude != null) {
				return;
			}
		}
		throw new ParseException(
				"File contains data rows, but none have valid coordinates. "
						+ "Check that the '" + fileSchema.latitudeColumn.header
						+ "' and '" + fileSchema.longitudeColumn.header
						+ "' columns contain non-empty latitude/longitude values.");
	}

    private void skipLines(BufferedReader r) throws IOException {
        int markLimit = 65_536;
        skippedLines = new ArrayList<>();

        // skip by pattern
        SkipLinesTo skipLinesTo = template.getSkipLinesTo();
        if (skipLinesTo != null) {
            Pattern pattern = Pattern.compile(skipLinesTo.getMatchRegex());
            while (true) {
                r.mark(markLimit);
                String line = r.readLine();
                if (line == null) {
                    r.reset();
                    break;
                }
                if (pattern.matches(line)) {
                    if (skipLinesTo.isSkipMatchedLine()) {
                        skippedLines.add(line);
                    } else {
                        r.reset();
                    }
                    break;
                }
            }
        }

        // skip commented lines before the header / first data line
        while (true) {
            r.mark(markLimit);
            String line = r.readLine();
            if (line == null) {
                r.reset();
                break;
            }
            if (isBlankOrCommented(line)) {
                skippedLines.add(line);
            } else {
                r.reset();
                break;
            }
        }
    }

    protected abstract String[] readHeaders(BufferedReader r) throws IOException;

    protected abstract String[] readValues(BufferedReader r) throws IOException;

	public boolean isBlankOrCommented(String line) {
		if (line == null) {
			return true;
		}
		int i = 0;
		while (i < line.length() && line.charAt(i) <= ' ') {
			i++;
		}
		if (i == line.length()) {
			return true;
		}

		String commentPrefix = template.getFileFormat().getCommentPrefix();
		return !Strings.isNullOrBlank(commentPrefix) && line.startsWith(commentPrefix, i);
	}

    private ColumnSchema buildValueSchema() {
        DataMapping mapping = template.getDataMapping();

        Map<String, BaseData> metaValues = mapping.getMetaValuesByHeader();
        ColumnSchema columns = new ColumnSchema();

        // add all file columns
        for (String header : headers.keySet()) {
            Column column = new Column(header);
            SensorData dataValue = mapping.getDataValueByHeader(header);
            if (dataValue != null) {
                column.setSemantic(Strings.emptyToNull(dataValue.getSemantic()));
                column.setUnit(Strings.emptyToNull(dataValue.getUnits()));
                column.setReadOnly(dataValue.isReadOnly());
            }
            BaseData metaValue = metaValues.get(header);
            if (metaValue != null) {
                // mark meta columns read-only
                column.setReadOnly(true);
                column.setSemantic(metaValue.getSemantic());
            }
            columns.addColumn(column);
        }

        // add columns declared in template but not present in a file
        for (SensorData dataValue : Nulls.toEmpty(mapping.getDataValues())) {
            if (dataValue == null) {
                continue;
            }
            String header = dataValue.getHeader();
            // present in a file
            if (hasHeader(header)) {
                continue;
            }
            String semantic = dataValue.getSemantic();
            // is line or mark column
            if (Objects.equals(semantic, Semantic.LINE.getName()) ||
                    Objects.equals(semantic, Semantic.MARK.getName())) {
                Column column = new Column(header)
                        .withSemantic(semantic)
                        .withUnit(Strings.emptyToNull(dataValue.getUnits()));
                columns.addColumn(column);
            }
        }

        return columns;
    }

	private FileSchema buildFileSchema(ColumnSchema valueSchema) {
		DataMapping mapping = template.getDataMapping();
		Map<String, BaseData> metaValues = mapping.getMetaValuesByHeader();
		Map<String, SensorData> dataValues = mapping.getDataValuesByHeader();

		String latitudeHeader = valueSchema.getHeaderBySemantic(Semantic.LATITUDE.getName());
		String longitudeHeader = valueSchema.getHeaderBySemantic(Semantic.LONGITUDE.getName());

		FileSchema fileSchema = new FileSchema();
		fileSchema.columns = new ArrayList<>(headers.size());
		for (Map.Entry<String, Integer> e : headers.entrySet()) {
			String header = e.getKey();
			FileColumn fileColumn = new FileColumn();
			fileColumn.header = header;
			fileColumn.index = e.getValue();
			fileColumn.valueIndex = valueSchema.getColumnIndex(header);
			if (fileColumn.valueIndex == -1) {
				continue;
			}
			fileColumn.isMeta = metaValues.containsKey(header);
			fileColumn.isTemplateValue = dataValues.containsKey(header);
			fileSchema.columns.add(fileColumn);

			if (header.equals(latitudeHeader)) {
				fileSchema.latitudeColumn = fileColumn;
			}
			if (header.equals(longitudeHeader)) {
				fileSchema.longitudeColumn = fileColumn;
			}
		}

		if (fileSchema.latitudeColumn == null || fileSchema.longitudeColumn == null) {
			throw new ParseException("Missing position headers: " + latitudeHeader + ", " + longitudeHeader + ".");
		}

		return fileSchema;
	}

    private void setColumnDisplay(FileSchema fileSchema, ColumnSchema valueSchema) {
		Set<String> displayHeaders = new HashSet<>();
		for (FileColumn fileColumn : fileSchema.columns) {
			// data column declared in template or having numeric values
			if (!fileColumn.isMeta && (fileColumn.isTemplateValue || fileColumn.hasNumbers)) {
				displayHeaders.add(fileColumn.header);
			}
		}

		for (Column column : valueSchema) {
			// explicitly hide marks and show lines
			String semantic = column.getSemantic();
			if (Objects.equals(semantic, Semantic.MARK.getName())) {
				column.setDisplay(false);
			} else if (Objects.equals(semantic, Semantic.LINE.getName())) {
				column.setDisplay(true);
			} else {
				column.setDisplay(displayHeaders.contains(column.getHeader()));
			}
		}
    }

    private GeoData parseValues(String[] tokens, FileSchema fileSchema, ColumnSchema valueSchema) {
		LocalDateTime dateTime = null;
		try {
			 dateTime = parseDateTime(tokens);
		} catch (IncorrectFormatException e) {
			warnings.addFormatError("Date-Time", e);
		}
        if (dateTime != null && template.isGpsTime()) {
            dateTime = GpsTime.gpsToUtc(dateTime);
        }

        GeoData geoData = new GeoData(valueSchema);
        geoData.setDateTime(dateTime);

        for (FileColumn column : fileSchema.columns) {
            String header = column.header;
			String str = Strings.emptyToNull(getString(tokens, column.index));
			if (str != null) {
				Numbers.ParseResult parsed = Numbers.parseNumber(str);
				if (!parsed.valid()) {
					// warn only for declared data values; meta columns (date, time, etc.)
					// are parsed separately and aren't expected to be numeric
					if (column.isTemplateValue) {
						warnings.addFormatError(header, header, "'" + str + "' is not a valid number");
					}
				} else if (parsed.number() != null) {
					column.hasNumbers = true;
				}
				geoData.setValue(column.valueIndex, Objects.requireNonNullElse(parsed.number(), str));
			}
        }

        // treat (0, 0) as a missing fix so it gets interpolated later
        Number latitude = geoData.getNumber(fileSchema.latitudeColumn.valueIndex);
		Number longitude = geoData.getNumber(fileSchema.longitudeColumn.valueIndex);
        if (latitude == null || longitude == null
				|| latitude.doubleValue() == 0.0 && longitude.doubleValue() == 0.0) {
            geoData.setValue(fileSchema.latitudeColumn.valueIndex, null);
            geoData.setValue(fileSchema.longitudeColumn.valueIndex, null);
        }
        return geoData;
    }

    // value parsers

	private String getString(String[] values, int tokenIndex) {
		if (values == null) {
			return null;
		}
		if (tokenIndex < 0 || tokenIndex >= values.length) {
			return null;
		}
		return values[tokenIndex];
	}

    private String getString(String[] values, String header) {
        if (values == null || header == null) {
            return null;
        }
        Integer columnIndex = headers.get(header);
        if (columnIndex == null || columnIndex < 0 || columnIndex >= values.length) {
            return null;
        }
        return values[columnIndex];
    }

    private String getString(String[] values, BaseData column) {
        if (column == null) {
            return null;
        }
        String value = getString(values, column.getHeader());
        if (column.getRegex() != null) {
            value = Text.matchPattern(value, column.getRegex());
        }
        return value;
    }

    private LocalDate parseDateFromFilename(String filename) {
        Date dateColumn = template.getDataMapping().getDate();
        String value = Text.matchPattern(filename, dateColumn.getRegex(), false);
        if (Strings.isNullOrEmpty(value)) {
            throw new ParseException("Incorrect file name. Cannot match date pattern");
        }

        LocalDate date = null;
        try {
            date = Text.parseDate(value, dateColumn.getAllFormats());
        } catch (IncorrectFormatException e) {
            warnings.addFormatError("Date", e);
        }
        return date;
    }

	private LocalDateTime parseDateTime(String[] values) {
		DataMapping mapping = template.getDataMapping();

		LocalDateTime dateTime = null;

		// dateTime column
		DateTime dateTimeColumn = mapping.getDateTime();
		if (hasHeader(dateTimeColumn)) {
			String value = getString(values, dateTimeColumn);
			dateTime = Text.parseDateTime(value, dateTimeColumn.getAllFormats());
		}
		if (dateTime != null) {
			return dateTime;
		}

		// date + time columns, or date from filename + time column
		DateTime timeColumn = mapping.getTime();
		if (hasHeader(timeColumn)) {
			String timeValue = getString(values, timeColumn);
			LocalTime time = Text.parseTime(timeValue, timeColumn.getAllFormats());
			if (time != null) {
				Date dateColumn = mapping.getDate();
				if (hasHeader(dateColumn)) {
					String dateValue = getString(values, dateColumn);
					LocalDate date = Text.parseDate(dateValue, dateColumn.getAllFormats());
					if (date != null) {
						dateTime = LocalDateTime.of(date, time);
					}
				}
				if (dateTime == null && dateFromFilename != null) {
					dateTime = LocalDateTime.of(dateFromFilename, time);
				}
			}
		}
		if (dateTime != null) {
			return dateTime;
		}

		// timestamp columns
		BaseData timestampColumn = mapping.getTimestamp();
		if (hasHeader(timestampColumn)) {
			Long timestamp = Text.parseLong(getString(values, timestampColumn));
			if (timestamp != null) {
				dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.of("UTC"));
			}
		}

		return dateTime;
	}

	static class FileColumn {

		String header;

		// column index within the line
		int index;

		// column index in the geoData value
		int valueIndex;

		boolean isMeta;

		boolean isTemplateValue;

		boolean hasNumbers;
	}

	static class FileSchema {

		List<FileColumn> columns;

		FileColumn latitudeColumn;

		FileColumn longitudeColumn;
	}
}

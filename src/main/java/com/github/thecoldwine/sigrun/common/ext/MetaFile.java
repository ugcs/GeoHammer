package com.github.thecoldwine.sigrun.common.ext;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvException;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.csv.DeclaredColumnOrder;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.FileNames;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MetaFile {

    private static final Logger log = LoggerFactory.getLogger(MetaFile.class);

    private static final String META_FILE_EXTENSION = ".geohammer";

    private List<TraceGeoData> values = new ArrayList<>();

    public List<? extends GeoData> getValues() {
        return values;
    }

    public static Path getMetaPath(File source) {
        Check.notNull(source);

        String sourceBase = FileNames.removeExtension(source.getName());
        String metaFileName = Strings.nullToEmpty(sourceBase) + META_FILE_EXTENSION;

        return new File(source.getParentFile(), metaFileName).toPath();
    }

    private CsvToBean<MetaRecord> newCsvReader(Reader reader) {
        Check.notNull(reader);

        return new CsvToBeanBuilder<MetaRecord>(reader)
                .withType(MetaRecord.class)
                .withIgnoreLeadingWhiteSpace(true)
                .withIgnoreEmptyLine(true)
                .withSeparator(',')
                .build();
    }

    private StatefulBeanToCsv<MetaRecord> newCsvWriter(Writer writer) {
        Check.notNull(writer);

        HeaderColumnNameMappingStrategy<MetaRecord> mappingStrategy
                = new HeaderColumnNameMappingStrategy<>();
        mappingStrategy.setType(MetaRecord.class);
        mappingStrategy.setColumnOrderOnWrite(new DeclaredColumnOrder(MetaRecord.class));

        return new StatefulBeanToCsvBuilder<MetaRecord>(writer)
                .withMappingStrategy(mappingStrategy)
                .withApplyQuotesToAll(false)
                .withSeparator(',')
                .build();
    }

    public void init(List<Trace> traces) {
        traces = Nulls.toEmpty(traces);
        List<TraceGeoData> values = new ArrayList<>(traces.size());
        for (int i = 0; i < traces.size(); i++) {
            Trace trace = traces.get(i);
            // construct geodata value for trace
            MetaRecord meta = new MetaRecord();
            meta.setTraceIndex(i);
            meta.setLineIndex(0);
            meta.setMark(trace.isMarked() ? 1 : 0);
            TraceGeoData value = meta.toGeoData();
            values.add(value);
        }
        this.values = values;
    }

    public void initLocations(List<Trace> traces) {
        for (TraceGeoData value : values) {
            int traceIndex = value.getTraceIndex();
            Trace trace = traces.get(traceIndex);

            value.setLatLon(trace.getLatLon());
        }
    }

    public void load(Path path) throws IOException {
        Check.notNull(path);

        List<TraceGeoData> values = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path)) {
            CsvToBean<MetaRecord> csvReader = newCsvReader(reader);

            for (MetaRecord metaRecord : csvReader) {
                values.add(metaRecord.toGeoData());
            }
        }
        this.values = values;
    }

    public void save(Path path) throws IOException {
        Check.notNull(path);

        try (Writer writer = Files.newBufferedWriter(path)) {
            StatefulBeanToCsv<MetaRecord> csvWriter = newCsvWriter(writer);

            for (TraceGeoData value : Nulls.toEmpty(values)) {
                MetaRecord metaRecord = MetaRecord.of(value);
                csvWriter.write(metaRecord);
            }
        } catch (CsvException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<Integer> getMarkIndices() {
        Set<Integer> marks = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            TraceGeoData value = values.get(i);
            if (value.isMarked()) {
                marks.add(i);
            }
        }
        return marks;
    }

    public void setMarkIndices(Set<Integer> marks) {
        for (int i = 0; i < values.size(); i++) {
            TraceGeoData value = values.get(i);
            value.setMarked(marks.contains(i));
        }
    }

    public static class MetaRecord {

        @CsvBindByName(column = "Trace")
        private Integer traceIndex;

        @CsvBindByName(column = "Line")
        private Integer lineIndex;

        @CsvBindByName(column = "Mark")
        private Integer mark;

        public Integer getTraceIndex() {
            return traceIndex;
        }

        public void setTraceIndex(Integer traceIndex) {
            this.traceIndex = traceIndex;
        }

        public Integer getLineIndex() {
            return lineIndex;
        }

        public void setLineIndex(Integer lineIndex) {
            this.lineIndex = lineIndex;
        }

        public Integer getMark() {
            return mark;
        }

        public void setMark(Integer mark) {
            this.mark = mark;
        }

        public static MetaRecord of(TraceGeoData value) {
            if (value == null) {
                return null;
            }
            MetaRecord record = new MetaRecord();
            record.setTraceIndex(value.getTraceIndex());
            record.setLineIndex(value.getLineIndex().orElse(null));
            record.setMark(value.isMarked() ? 1 : 0);
            return record;
        }

        public TraceGeoData toGeoData() {
            Check.notNull(traceIndex);

            TraceGeoData value = new TraceGeoData(traceIndex);
            if (lineIndex != null) {
                value.setLineIndex(lineIndex);
            }
            if (mark != null) {
                value.setMarked(mark != 0);
            }
            return value;
        }
    }
}

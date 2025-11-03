package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.AppContext;
import com.ugcs.gprvisualizer.app.auxcontrol.FoundPlace;
import com.ugcs.gprvisualizer.app.parsers.ColumnSchema;
import com.ugcs.gprvisualizer.app.parsers.GeoData;
import com.ugcs.gprvisualizer.app.parsers.csv.CsvParser;
import com.ugcs.gprvisualizer.app.parsers.csv.CsvParserFactory;
import com.ugcs.gprvisualizer.app.parsers.csv.CsvWriter;
import com.ugcs.gprvisualizer.app.undo.FileSnapshot;
import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CsvFile extends SgyFile {

	private static final Logger log = LoggerFactory.getLogger(CsvFile.class.getName());

	private List<GeoData> geoData = new ArrayList<>();

	@Nullable
	private CsvParser parser;

	private FileTemplates fileTemplates;

	public CsvFile(FileTemplates fileTemplates) {
		this.fileTemplates = fileTemplates;
	}

    private CsvFile(CsvFile file) {
        this(file.fileTemplates);
        this.setFile(file.getFile());
        this.parser = file.parser;
    }

    @Override
    public int numTraces() {
        return geoData.size();
    }

    @Override
    public void open(File csvFile) throws IOException {
        Template template = fileTemplates.findTemplate(fileTemplates.getTemplates(), csvFile);
        if (template == null) {
            throw new RuntimeException("Can`t find template for file " + csvFile.getName());
        }

        log.debug("template: {}", template.getName());

        parser = new CsvParserFactory().createCsvParser(template);
        geoData = parser.parse(csvFile);

        if (getFile() == null) {
            setFile(csvFile);
        }

        // create marks
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            boolean marked = value.getMarkOrDefault(false);
            if (marked) {
                // create mark
                TraceKey traceKey = new TraceKey(this, i);
                getAuxElements().add(new FoundPlace(traceKey, AppContext.model));
            }
        }

        reorderLines();

        setUnsaved(false);
    }

    private void reorderLines() {
        Integer prevLine = null;
        int sequence = 0;
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            Integer line = value.getLine();
            if (i > 0 && !Objects.equals(line, prevLine)) {
                sequence++;
            }
            value.setLine(sequence);
            prevLine = line;
        }
    }

    @Override
    public void save(File file) throws IOException {
        Check.notNull(file);

        var marks = getAuxElements().stream()
                .filter(bo -> bo instanceof FoundPlace)
                .map(bo -> ((FoundPlace) bo).getTraceIndex())
                .collect(Collectors.toSet());
        for (int i = 0; i < geoData.size(); i++) {
            GeoData value = geoData.get(i);
            value.setMark(marks.contains(i));
        }

        CsvWriter writer = new CsvWriter(this);
        writer.write(file);
    }

    @Override
    public List<GeoData> getGeoData() {
        return geoData;
    }

    public void setGeoData(List<GeoData> geoData) {
        this.geoData = geoData;
    }

    @Nullable
    public CsvParser getParser() {
        return parser;
    }

    @Nullable
    public Template getTemplate() {
        CsvParser parser = this.parser;
        return parser != null ? parser.getTemplate() : null;
    }

    @Override
    public CsvFile copy() {
        return new CsvFile(this);
    }

    @Override
    public FileSnapshot<CsvFile> createSnapshot() {
        return new Snapshot(this);
    }

	public void loadFrom(CsvFile other) {
        this.parser = other.getParser();
		this.setGeoData(other.getGeoData());
		this.setAuxElements(other.getAuxElements());
		this.setUnsaved(true);
	}

    public boolean isSameTemplate(CsvFile file) {
        return Objects.equals(file.getTemplate(), getTemplate());
    }

    public static class Snapshot extends FileSnapshot<CsvFile> {

        private final List<GeoData> values;

        public Snapshot(CsvFile file) {
            super(file);

            this.values = copyValues(file);
        }

        private static List<GeoData> copyValues(CsvFile file) {
            List<GeoData> values = Nulls.toEmpty(file.getGeoData());
            List<GeoData> snapshot = new ArrayList<>(values.size());
            ColumnSchema copySchema = ColumnSchema.copy(GeoData.getSchema(values));
            for (GeoData value : values) {
                GeoData copy = value != null
                        ? new GeoData(copySchema, value)
                        : null;
                snapshot.add(copy);
            }
            return snapshot;
        }

        @Override
        public void restoreFile(Model model) {
            file.setGeoData(values);
        }
    }
}

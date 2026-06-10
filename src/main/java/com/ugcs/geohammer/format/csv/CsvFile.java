package com.ugcs.geohammer.format.csv;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.format.csv.parser.Parser;
import com.ugcs.geohammer.format.csv.parser.Writer;
import com.ugcs.geohammer.format.csv.parser.WriterFactory;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.IndexRange;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.TraceKey;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.format.csv.parser.ParserFactory;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.model.undo.GeoDataCodec;
import com.ugcs.geohammer.model.undo.TempStore;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.util.MissingValues;
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
	private Parser parser;

	private final FileTemplates fileTemplates;

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
        Template template = fileTemplates.findTemplate(csvFile);
        if (template == null) {
            throw new RuntimeException("Can`t find template for file " + csvFile.getName());
        }

        log.debug("template: {}", template.getName());

        parser = ParserFactory.createParser(template);
        geoData = parser.parse(csvFile);

        MissingValues.fillGeoDataPositions(geoData);

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

        Writer writer = WriterFactory.createWriter(getTemplate());
        writer.write(this, file);
    }

    @Override
    public void save(File file, IndexRange range) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<GeoData> getGeoData() {
        return geoData;
    }

    public void setGeoData(List<GeoData> geoData) {
        this.geoData = geoData;
    }

    @Nullable
    public Parser getParser() {
        return parser;
    }

    @Nullable
    public Template getTemplate() {
        Parser parser = this.parser;
        return parser != null ? parser.getTemplate() : null;
    }

	public boolean isPositional() {
		Template template = getTemplate();
		return template != null ? template.isPositional() : FileTypes.isPositionFile(getFile());
	}

	@Override
    public CsvFile copy() {
        return new CsvFile(this);
    }

    @Override
    public FileSnapshot<CsvFile> createSnapshot() {
        try {
            return new Snapshot(this);
        } catch (IOException e) {
            log.error("Failed to create snapshot", e);
            return null;
        }
    }

	public void loadFrom(CsvFile other) {
        this.parser = other.getParser();
		this.setGeoData(other.getGeoData());
		this.setAuxElements(other.getAuxElements());
		this.setUnsaved(true);
	}

    public static class Snapshot extends FileSnapshot<CsvFile> {

        private final TempStore.Entry valuesEntry;

        public Snapshot(CsvFile file) throws IOException {
            super(file);

            TempStore tempStore = AppContext.getInstance(TempStore.class);
            valuesEntry = tempStore.newEntry();
            valuesEntry.write(out -> GeoDataCodec.write(out, file.getGeoData()));
        }

        @Override
        public void restoreFile(Model model) throws IOException {
            List<GeoData> values = valuesEntry.read(GeoDataCodec::read);
            file.setGeoData(values);
        }

        @Override
        public void discard() {
            valuesEntry.close();
        }
    }
}

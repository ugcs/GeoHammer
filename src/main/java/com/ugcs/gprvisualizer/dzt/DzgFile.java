package com.ugcs.gprvisualizer.dzt;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;

/**
 * satellite file with GPS coorinates.
 *
 * @author Kesha
 */
public class DzgFile {

    private final NavigableMap<Integer, Record> records = new TreeMap<>();

    private boolean exists = false;

    public boolean hasIndex(int traceIndex) {
        return records.containsKey(traceIndex);
    }

    public LatLon getLatLon(int traceIndex) {
        if (!exists) {
            return null;
        }

        Record exact = records.get(traceIndex);
        if (exact != null) {
            return exact.latLon;
        }

        var floor = records.floorEntry(traceIndex);
        var ceiling = records.ceilingEntry(traceIndex);

        if (floor == null) {
            return ceiling != null ? ceiling.getValue().latLon : null;
        }
        if (ceiling == null) {
            return floor.getValue().latLon;
        }

        double k = ((double) (traceIndex - floor.getKey()))
                / ((double) (ceiling.getKey() - floor.getKey()));
        LatLon from = floor.getValue().latLon;
        LatLon to = ceiling.getValue().latLon;
        return new LatLon(
                from.getLatDgr() + k * (to.getLatDgr() - from.getLatDgr()),
                from.getLonDgr() + k * (to.getLonDgr() - from.getLonDgr())
        );
    }

    public void load(File file) throws IOException {
        Check.notNull(file);

        if (!file.exists()) {
            exists = false;
            return;
        }

        records.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Record record = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("$GSSIS")) {
                    record = new Record();
                    records.put(parseIndex(line), record);
                    record.line1 = line;
                } else if (line.startsWith("$GPGGA")) {
                    if (record != null) {
                        record.line2 = line;
                        record.latLon = parseLatLon(line);
                    }
                }
            }
            exists = true;
        }
    }

    private static int parseIndex(String line) {
        Check.notNull(line);

        return Integer.parseInt(line.split(",")[1]);
    }

    private static String replaceIndex(String line, int traceIndex) {
        String[] tokens = line.split(",");
        tokens[1] = Integer.toString(traceIndex);
        return String.join(",", tokens);
    }

    private LatLon parseLatLon(String line) {
        Check.notNull(line);

        String[] tokens = line.split(",");

        double northSouth = ("N".equals(tokens[3]) ? 1 : -1);
        double westEast = ("E".equals(tokens[5]) ? 1 : -1);

        double lat = Double.parseDouble(tokens[2]) * northSouth;
        double lon = Double.parseDouble(tokens[4]) * westEast;

        double rlon = TraceFile.convertDegreeFraction(lon);
        double rlat = TraceFile.convertDegreeFraction(lat);

        return new LatLon(rlat, rlon);
    }

    public void save(File file, List<IndexMapping> indexMappings) throws IOException {
        Check.notNull(file);
        Check.notNull(indexMappings);

        // mapping allows to override trace indices
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (IndexMapping indexMapping : Nulls.toEmpty(indexMappings)) {
                int index = indexMapping.index;
                int sourceIndex = indexMapping.sourceIndex;

                Record record = records.get(sourceIndex);
                if (record == null) {
                    continue;
                }

                writer.append(replaceIndex(record.line1, index));
                writer.newLine();
                writer.append(record.line2);
                writer.newLine();
                writer.newLine();
            }
        }
    }

    static class Record {
        String line1;
        String line2;
        LatLon latLon;
    }

    public static class IndexMapping {

        // new index
        private final int index;

        // trace index in a source file
        private final int sourceIndex;

        public IndexMapping(int index, int sourceIndex) {
            this.index = index;
            this.sourceIndex = sourceIndex;
        }

        public int getIndex() {
            return index;
        }

        public int getSourceIndex() {
            return sourceIndex;
        }
    }
}

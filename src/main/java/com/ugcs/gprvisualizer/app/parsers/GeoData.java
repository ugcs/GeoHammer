package com.ugcs.gprvisualizer.app.parsers;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.yaml.DataMapping;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GeoData extends GeoCoordinates {

    /**
     * Original line from source file
     */
    private String[] sourceLine;

    private boolean marked;

    private List<SensorValue> sensorValues;

    public GeoData(double latitude, double longitude) {
        super(latitude, longitude);
    }

    public GeoData(GeoData other) {
        super(other);

        this.sourceLine = other.sourceLine;
        this.marked = other.marked;
        this.sensorValues = new ArrayList<>();
        for (SensorValue sensorValue : other.sensorValues) {
            sensorValues.add(new SensorValue(sensorValue));
        }
    }

	public String[] getSourceLine() {
		return sourceLine;
	}

	public void setSourceLine(String[] sourceLine) {
		this.sourceLine = sourceLine;
	}

    public boolean isMarked() {
        return marked;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public List<SensorValue> getSensorValues() {
        return sensorValues;
    }

    public SensorValue getSensorValue(String header) {
        if (Strings.isNullOrEmpty(header)) {
            return null;
        }
        SensorValue result = null;
        for (SensorValue sensorValue : sensorValues) {
            if (header.equals(sensorValue.header())) {
                result = sensorValue;
                break;
            }
        }
        return result;
    }

    public void setSensorValues(List<SensorValue> sensorValues) {
        this.sensorValues = sensorValues;
    }

    public void setSensorValue(String header, Number value) {
        if (Strings.isNullOrEmpty(header)) {
            return;
        }
        for(SensorValue sensorValue : sensorValues) {
            if (header.equals(sensorValue.header())) {
                sensorValues.add(sensorValue.withValue(value));
                sensorValues.remove(sensorValue);
                return;
            }
        }
        sensorValues.add(new SensorValue(header, "", value, value));
    }

    public void undoSensorValue(String header) {
        if (Strings.isNullOrEmpty(header)) {
            return;
        }
        for (SensorValue sensorValue : sensorValues) {
            if (header.equals(sensorValue.header())) {
                sensorValues.add(sensorValue.withValue(sensorValue.originalData()));
                sensorValues.remove(sensorValue);
                break;
            }
        }
    }

    public Optional<Integer> getInt(String header) {
        SensorValue sensorValue = getSensorValue(header);
        return sensorValue != null && sensorValue.data() != null
                ? Optional.of(sensorValue.data().intValue())
                : Optional.empty();
    }

    public Optional<Double> getDouble(String header) {
        SensorValue sensorValue = getSensorValue(header);
        return sensorValue != null && sensorValue.data() != null
                ? Optional.of(sensorValue.data().doubleValue())
                : Optional.empty();
    }

    // static utility helpers

    public static String getHeaderInFile(Semantic semantic, SgyFile file) {
        return getHeaderInFile(semantic.getName(), file);
    }

    public static String getHeaderInFile(String semantic, SgyFile file) {
        if (file instanceof TraceFile traceFile) {
            return getHeader(semantic, null);
        }
        if (file instanceof CsvFile csvFile) {
            return getHeader(semantic, Check.notNull(csvFile.getTemplate()));
        }
        return null;
    }

    public static String getHeader(Semantic semantic, Template template) {
        return getHeader(semantic.getName(), template);
    }

    public static String getHeader(String semantic, Template template) {
        if (template != null) {
            DataMapping mapping = template.getDataMapping();
            if (mapping != null) {
                return mapping.getHeaderBySemantic(semantic);
            }
        }
        // use semantic name as a header when no template
        return semantic;
    }
}

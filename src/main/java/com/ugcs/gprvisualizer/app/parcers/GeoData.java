package com.ugcs.gprvisualizer.app.parcers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GeoData extends GeoCoordinates {

    public static final String ANOMALY_SEMANTIC_SUFFIX = "_anomaly";

    public enum Semantic {

        LINE("Line"),
        ALTITUDE_AGL("Altitude AGL"),
        TMI("TMI"), MARK("Mark"),;

        private final String name;

        Semantic(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private final List<SensorValue> sensorValues;

    /**
     * Original line from source file
     */
    private String sourceLine;

    private final boolean marked;

    public GeoData(boolean marked, String sourceLine, List<SensorValue> sensorValues, GeoCoordinates geoCoordinates) {
        super(geoCoordinates.getLatitude(), geoCoordinates.getLongitude(), geoCoordinates.getAltitude(), geoCoordinates.getTimeInMs(), geoCoordinates.getTraceNumber(), geoCoordinates.getDateTime());
        this.sensorValues = sensorValues;
        this.sourceLine = sourceLine;
        this.marked = marked;
    }

    public GeoData(GeoData geoData) {
        super(geoData.getLatitude(), geoData.getLongitude(), geoData.getAltitude(), geoData.getTimeInMs(), geoData.getTraceNumber(), geoData.getDateTime());
        this.sensorValues = new ArrayList<>();
        for (SensorValue sensorValue : geoData.sensorValues) {
            sensorValues.add(new SensorValue(sensorValue));
        }
        this.sourceLine = geoData.sourceLine;
        this.marked = geoData.marked;
    }

    public List<SensorValue> getSensorValues() {
        return sensorValues;
    }

	public String getSourceLine() {
		return sourceLine;
	}

	public void setSourceLine(String sourceLine) {
		this.sourceLine = sourceLine;
	}

	public void setLineIndex(int lineNumber) {
        setSensorValue(Semantic.LINE.name, lineNumber);
    }

    public int getLineIndexOrDefault() {
        return getLineIndex().orElse(0);
    }

    public Optional<Integer> getLineIndex() {
        return getInt(Semantic.LINE.name);
    }

    public Optional<Double> getDouble(String semantic) {
        SensorValue sensorValue = getSensorValue(semantic);
        return sensorValue != null && sensorValue.data() != null
                ? Optional.of(sensorValue.data().doubleValue())
                : Optional.empty();
    }

    public Optional<Integer> getInt(String semantic) {
        SensorValue sensorValue = getSensorValue(semantic);
        return sensorValue != null && sensorValue.data() != null
                ? Optional.of(sensorValue.data().intValue())
                : Optional.empty();
    }

    public SensorValue getLine() {
        return getSensorValue(Semantic.LINE);    
    }

    public SensorValue getSensorValue(Semantic semantic) {
        return getSensorValue(semantic.name);
    }

    public SensorValue getSensorValue(String semantic) {
        SensorValue result = null;
        for(SensorValue sensorValue : sensorValues) {
            if (semantic.equals(sensorValue.semantic())) {
                result = sensorValue;
                break;
            }
        }
        return result;
    }

    public void setSensorValue(String semantic, Number value) {
        for(SensorValue sensorValue : sensorValues) {
            if (semantic.equals(sensorValue.semantic())) {
                sensorValues.add(sensorValue.withValue(value));
                sensorValues.remove(sensorValue);
                return;
            }
        }
        sensorValues.add(new SensorValue(semantic, "", value, value));
    }

    public void undoSensorValue(String semantic) {
        for(SensorValue sensorValue : sensorValues) {
            if (semantic.equals(sensorValue.semantic())) {
                sensorValues.add(sensorValue.withValue(sensorValue.originalData()));
                sensorValues.remove(sensorValue);
                break;
            }
        }
    }

    public boolean isMarked() {
        return marked;
    }
}

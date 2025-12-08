package com.ugcs.geohammer.model.template;

import com.ugcs.geohammer.model.template.data.Altitude;
import com.ugcs.geohammer.model.template.data.BaseData;
import com.ugcs.geohammer.model.template.data.Date;
import com.ugcs.geohammer.model.template.data.DateTime;
import com.ugcs.geohammer.model.template.data.Latitude;
import com.ugcs.geohammer.model.template.data.Longitude;
import com.ugcs.geohammer.model.template.data.SensorData;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NullUnmarked
public class DataMapping {

    private Latitude latitude;

    private Longitude longitude;

    private Altitude altitude;

    private Date date;

    private DateTime time;

    private DateTime dateTime;

    private BaseData timestamp;

    private BaseData traceNumber;

    private List<BaseData> sgyTraces;

    private List<SensorData> dataValues;

    // lookup caches
    private Map<String, SensorData> dataValuesBySemantic;

    private Map<String, SensorData> dataValuesByHeader;

    public List<BaseData> getSgyTraces() {
        return sgyTraces;
    }

    public void setSgyTraces(List<BaseData> sgyTraces) {
        this.sgyTraces = sgyTraces;
    }

    /**
     * Gets the sensors of the template.
     *
     * @return the sensors of the template.
     */
    public List<SensorData> getDataValues() {
        return dataValues;
    }

    public void setDataValues(List<SensorData> dataValues) {
        this.dataValues = dataValues;
    }

    public Latitude getLatitude() {
        return latitude;
    }

    public void setLatitude(Latitude latitude) {
        this.latitude = latitude;
    }

    public Longitude getLongitude() {
        return longitude;
    }

    public void setLongitude(Longitude longitude) {
        this.longitude = longitude;
    }

    public Altitude getAltitude() {
        return altitude;
    }

    public void setAltitude(Altitude altitude) {
        this.altitude = altitude;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public DateTime getTime() {
        return time;
    }

    public void setTime(DateTime time) {
        this.time = time;
    }

    public DateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
    }

    public BaseData getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(BaseData timestamp) {
        this.timestamp = timestamp;
    }

    public BaseData getTraceNumber() {
        return traceNumber;
    }

    public void setTraceNumber(BaseData traceNumber) {
        this.traceNumber = traceNumber;
    }

    public List<BaseData> getMetaValues() {
        List<BaseData> values = new ArrayList<>();

        Nulls.ifPresent(latitude, values::add);
        Nulls.ifPresent(longitude, values::add);
        Nulls.ifPresent(altitude, values::add);
        Nulls.ifPresent(date, values::add);
        Nulls.ifPresent(time, values::add);
        Nulls.ifPresent(dateTime, values::add);
        Nulls.ifPresent(timestamp, values::add);
        Nulls.ifPresent(traceNumber, values::add);

        return values;
    }

    public Map<String, BaseData> getMetaValuesByHeader() {
        List<BaseData> metaValues = getMetaValues();
        Map<String, BaseData> metaValuesByHeader = new HashMap<>(metaValues.size());
        for (BaseData metaValue : metaValues) {
            String header = metaValue.getHeader();
            if (!Strings.isNullOrBlank(header)) {
                metaValuesByHeader.put(header, metaValue);
            }
        }
        return metaValuesByHeader;
    }

    public List<@Nullable BaseData> getIndexedValues() {
        List<BaseData> values = new ArrayList<>();
        values.addAll(getMetaValues());
        values.addAll(Nulls.toEmpty(dataValues));

        int maxIndex = -1;
        for (BaseData value : values) {
            if (value.getIndex() != null) {
                maxIndex = Math.max(maxIndex, value.getIndex());
            }
        }
        if (maxIndex == -1) {
            return List.of();
        }

        List<@Nullable BaseData> indexedValues
                = new ArrayList<>(Collections.nCopies(maxIndex + 1, null));
        for (BaseData value : values) {
            if (value.getIndex() != null && value.getIndex() >= 0) {
                indexedValues.set(value.getIndex(), value);
            }
        }
        return indexedValues;
    }

    public List<String> getIndexedHeaders() {
        List<@Nullable BaseData> indexedValues = getIndexedValues();
        List<String> indexedHeaders = new ArrayList<>(indexedValues.size());
        for (int i = 0; i < indexedValues.size(); i++) {
            BaseData value = indexedValues.get(i);
            String header = value != null
                    ? Strings.trim(value.getHeader())
                    : null;
            if (header == null) {
                header = "column_" + i;
            }
            indexedHeaders.add(header);
        }
        return indexedHeaders;
    }

    public boolean addDataValue(SensorData sensorData) {
        Check.notNull(sensorData);
        // semantic is required
        String semantic = sensorData.getSemantic();
        Check.notNull(semantic);
        // either header or index is set
        String header = sensorData.getHeader();
        Check.condition(!Strings.isNullOrEmpty(header)
                || sensorData.getIndex() != null);

        if (getDataValueBySemantic(semantic) != null) {
            return false;
        }
        if (!Strings.isNullOrEmpty(header) && getDataValueByHeader(header) != null) {
            return false;
        }

        if (dataValues == null) {
            dataValues = new ArrayList<>(1);
        }
        dataValues.add(sensorData);
        dataValuesBySemantic.put(semantic, sensorData);
        if (!Strings.isNullOrEmpty(header)) {
            dataValuesByHeader.put(header, sensorData);
        }
        return true;
    }

    public Map<String, SensorData> getDataValuesBySemantic() {
        if (dataValuesBySemantic == null) {
            dataValuesBySemantic = new HashMap<>(Nulls.toEmpty(dataValues).size());
            for (SensorData sensorData : Nulls.toEmpty(dataValues)) {
                if (sensorData == null) {
                    continue;
                }
                String semantic = sensorData.getSemantic();
                if (!Strings.isNullOrBlank(semantic)) {
                    dataValuesBySemantic.put(semantic, sensorData);
                }
            }
        }
        return dataValuesBySemantic;
    }

    public Map<String, SensorData> getDataValuesByHeader() {
        if (dataValuesByHeader == null) {
            dataValuesByHeader = new HashMap<>(Nulls.toEmpty(dataValues).size());
            for (SensorData sensorData : Nulls.toEmpty(dataValues)) {
                if (sensorData == null) {
                    continue;
                }
                String header = sensorData.getHeader();
                if (!Strings.isNullOrBlank(header)) {
                    dataValuesByHeader.put(header, sensorData);
                }
            }
        }
        return dataValuesByHeader;
    }

    public SensorData getDataValueBySemantic(String semantic) {
        return getDataValuesBySemantic().get(semantic);
    }

    public SensorData getDataValueByHeader(String header) {
        return getDataValuesByHeader().get(header);
    }

    public String getHeaderBySemantic(String semantic) {
        SensorData sensorData = getDataValueBySemantic(semantic);
        return sensorData != null
                ? Strings.emptyToNull(sensorData.getHeader())
                : null;
    }

    public String getSemanticByHeader(String header) {
        SensorData sensorData = getDataValueByHeader(header);
        return sensorData != null
                ? Strings.emptyToNull(sensorData.getSemantic())
                : null;
    }
}

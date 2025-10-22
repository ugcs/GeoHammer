package com.ugcs.gprvisualizer.app.yaml;

import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.app.yaml.data.DateTime;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.jspecify.annotations.NullUnmarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NullUnmarked
public class DataMapping {

    private BaseData latitude;
    private BaseData longitude;
    private BaseData altitude;
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

    public BaseData getLatitude() {
        return latitude;
    }

    public void setLatitude(BaseData latitude) {
        this.latitude = latitude;
    }

    public BaseData getLongitude() {
        return longitude;
    }

    public void setLongitude(BaseData longitude) {
        this.longitude = longitude;
    }

    public BaseData getAltitude() {
        return altitude;
    }

    public void setAltitude(BaseData altitude) {
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

    public List<BaseData> getAllValues() {
        List<BaseData> columns = new ArrayList<>();

        Nulls.ifPresent(latitude, columns::add);
        Nulls.ifPresent(longitude, columns::add);
        Nulls.ifPresent(altitude, columns::add);
        Nulls.ifPresent(date, columns::add);
        Nulls.ifPresent(time, columns::add);
        Nulls.ifPresent(dateTime, columns::add);
        Nulls.ifPresent(timestamp, columns::add);
        Nulls.ifPresent(traceNumber, columns::add);

        for (BaseData column : Nulls.toEmpty(dataValues)) {
            Nulls.ifPresent(column, columns::add);
        }

        for (BaseData column : Nulls.toEmpty(sgyTraces)) {
            Nulls.ifPresent(column, columns::add);
        }

        return columns;
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

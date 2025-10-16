package com.ugcs.gprvisualizer.app.yaml;

import com.ugcs.gprvisualizer.app.yaml.data.BaseData;
import com.ugcs.gprvisualizer.app.yaml.data.Date;
import com.ugcs.gprvisualizer.app.yaml.data.DateTime;
import com.ugcs.gprvisualizer.app.yaml.data.SensorData;
import com.ugcs.gprvisualizer.utils.Nulls;
import com.ugcs.gprvisualizer.utils.Strings;
import org.jspecify.annotations.NullUnmarked;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public List<BaseData> getAllColumns() {
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

    // header -> data column
    public Map<String, SensorData> getDataColumns() {
        Map<String, SensorData> dataColumns = new HashMap<>(Nulls.toEmpty(dataValues).size());
        for (SensorData sensorData : Nulls.toEmpty(dataValues)) {
            if (sensorData != null) {
                dataColumns.put(sensorData.getHeader(), sensorData);
            }
        }
        return dataColumns;
    }

    public SensorData getDataColumnBySemantic(String semantic) {
        for (SensorData sensorData : Nulls.toEmpty(dataValues)) {
            if (sensorData != null && Objects.equals(sensorData.getSemantic(), semantic)) {
                return sensorData;
            }
        }
        return null;
    }

    public SensorData getDataColumnByHeader(String header) {
        for (SensorData sensorData : Nulls.toEmpty(dataValues)) {
            if (sensorData != null && Objects.equals(sensorData.getHeader(), header)) {
                return sensorData;
            }
        }
        return null;
    }

    public String getHeaderBySemantic(String semantic) {
        SensorData column = getDataColumnBySemantic(semantic);
        return column != null ? Strings.emptyToNull(column.getHeader()) : null;
    }

    public boolean addDataColumn(String semantic, String header, String units) {
        SensorData column = new SensorData();
        column.setSemantic(semantic);
        column.setHeader(header);
        column.setUnits(units);
        return addDataColumn(column);
    }

    public boolean addDataColumn(SensorData column) {
        if (column == null) {
            return false;
        }
        for (SensorData templateColumn : Nulls.toEmpty(dataValues)) {
            if (Objects.equals(column.getSemantic(), templateColumn.getSemantic())) {
                // column with a matching semantic exists
                return false;
            }
        }
        if (dataValues == null) {
            dataValues = new ArrayList<>(1);
        }
        dataValues.add(column);
        return true;
    }
}

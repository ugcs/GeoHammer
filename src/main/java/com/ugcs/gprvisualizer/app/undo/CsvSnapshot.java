package com.ugcs.gprvisualizer.app.undo;

import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.gpr.Model;
import com.ugcs.gprvisualizer.utils.Nulls;

import java.util.ArrayList;
import java.util.List;

public class CsvSnapshot extends FileSnapshot<CsvFile> {

    private final List<GeoData> values;

    public CsvSnapshot(CsvFile file) {
        super(file);

        this.values = copyValues(file);
    }

    private static List<GeoData> copyValues(CsvFile file) {
        List<GeoData> values = Nulls.toEmpty(file.getGeoData());
        List<GeoData> snapshot = new ArrayList<>(values.size());
        for (GeoData value : values) {
            snapshot.add(new GeoData(value));
        }
        return snapshot;
    }

    @Override
    public void restoreFile(Model model) {
        file.setGeoData(values);
    }
}

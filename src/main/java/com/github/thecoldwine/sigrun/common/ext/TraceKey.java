package com.github.thecoldwine.sigrun.common.ext;

import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.utils.Check;
import com.ugcs.gprvisualizer.utils.Nulls;

import java.util.List;

public class TraceKey {

    private final SgyFile file;

    private final int index;

    public TraceKey(SgyFile file, int index) {
        Check.notNull(file);

        this.file = file;
        this.index = index;
    }

    public SgyFile getFile() {
        return file;
    }

    public int getIndex() {
        return index;
    }

    public LatLon getLatLon() {
        List<GeoData> values = Nulls.toEmpty(file.getGeoData());
        return values.get(index).getLatLon();
    }
}

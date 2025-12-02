package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;

public class SeriesSelectedEvent extends BaseEvent {

    private final SgyFile file;

    private final String seriesName;

    public SeriesSelectedEvent(Object source, SgyFile file, String seriesName) {
        super(source);

        this.file = file;
        this.seriesName = seriesName;
    }

    public SgyFile getFile() {
        return file;
    }

    public String getSeriesName() {
        return seriesName;
    }
}

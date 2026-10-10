package com.ugcs.geohammer.chart.tool;

import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.model.ColumnSchema;
import javafx.scene.control.ComboBox;

final class MagneticSeriesSelector {

    private MagneticSeriesSelector() {
    }

    static void update(ComboBox<String> selector, SgyFile file, String preferredSeries) {
        String selectedSeries = preferredSeries != null ? preferredSeries : selector.getValue();
        selector.getItems().clear();
        selector.setValue(null);

        ColumnSchema schema = GeoData.getSchema(file != null ? file.getGeoData() : null);
        if (schema != null) {
            selector.getItems().addAll(schema.getDisplayHeaders());
        }
        if (selector.getItems().contains(selectedSeries)) {
            selector.setValue(selectedSeries);
        } else if (!selector.getItems().isEmpty()) {
            selector.setValue(selector.getItems().getFirst());
        }
    }
}

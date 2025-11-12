package com.ugcs.geohammer.format.kml;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.element.ConstPlace;
import com.ugcs.geohammer.model.Model;
import de.micromata.opengis.kml.v_2_2_0.Coordinate;
import de.micromata.opengis.kml.v_2_2_0.Feature;
import de.micromata.opengis.kml.v_2_2_0.Folder;
import de.micromata.opengis.kml.v_2_2_0.Kml;
import de.micromata.opengis.kml.v_2_2_0.Document;
import de.micromata.opengis.kml.v_2_2_0.Placemark;
import de.micromata.opengis.kml.v_2_2_0.Point;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

public class KmlReader {

    public void read(File file, Model model) {
        Kml kml = Kml.unmarshal(file);

        Document doc = (Document) kml.getFeature();
        List<Feature> features = doc.getFeature();

        features.stream()
            .flatMap(f -> {
                if (f instanceof Placemark) {
                    return Stream.of((((Placemark) f)));
                }
                if (f instanceof Folder) {
                    return (((Folder) f).getFeature()).stream()
                        .map(x -> (Placemark) x);
                }
                throw new RuntimeException("Uknown format of KML");
            })
        .forEach(f -> {
            Point pm = (Point) f.getGeometry();
            Coordinate c = pm.getCoordinates().getFirst();

            LatLon latlon = new LatLon(c.getLatitude(), c.getLongitude());

            ConstPlace constPlace = new ConstPlace(0, latlon);
            model.getAuxElements().add(constPlace);
        });

        model.setKmlToFlagAvailable(true);
    }
}

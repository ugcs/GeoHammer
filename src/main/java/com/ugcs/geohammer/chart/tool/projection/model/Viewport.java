package com.ugcs.geohammer.chart.tool.projection.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.springframework.stereotype.Component;

@Component
public class Viewport {

    private static final double FIT_FACTOR = 0.92;

    private final BooleanProperty zoomToProfile = new SimpleBooleanProperty(false);

    // world space
    // x - distance from the start of the line
    // y - ellipsoidal height
    private final ObjectProperty<Point2D> origin = new SimpleObjectProperty<>(new Point2D(0, 0));

    // meters per pixel, y-inverted
    private final ObjectProperty<Point2D> scale = new SimpleObjectProperty<>(new Point2D(1, -1));

    public boolean isZoomToProfile() {
        return zoomToProfile.get();
    }

    public BooleanProperty zoomToProfileProperty() {
        return zoomToProfile;
    }

    public Point2D getOrigin() {
        return origin.get();
    }

    public ObjectProperty<Point2D> originProperty() {
        return origin;
    }

    public Point2D getScale() {
        return scale.get();
    }

    public ObjectProperty<Point2D> scaleProperty() {
        return scale;
    }

    public Point2D fromWorld(Point2D world) {
        return new Point2D(
                (world.getX() - origin.get().getX()) / scale.get().getX(),
                (world.getY() - origin.get().getY()) / scale.get().getY()
        );
    }

    public Point2D toWorld(Point2D local) {
        return new Point2D(
                local.getX() * scale.get().getX() + origin.get().getX(),
                local.getY() * scale.get().getY() + origin.get().getY()
        );
    }

    public void fit(Rectangle2D envelope, double width, double height) {
        if (envelope == null) {
            return;
        }
        if (width <= 0 || height <= 0) {
            return;
        }

        double minX = envelope.getMinX();
        double minY = envelope.getMinY();
        double maxX = envelope.getMaxX();
        double maxY = envelope.getMaxY();

        // flip Y: world Y-up -> canvas Y-down
        double factor = Math.max(
                maxX > minX ? (maxX - minX) / (FIT_FACTOR * width) : 1,
                maxY > minY ? (maxY - minY) / (FIT_FACTOR * height) : 1
        );
        scale.set(new Point2D(
                factor,
                -factor));
        origin.set(new Point2D(
                (minX + maxX) / 2 - scale.get().getX() * width / 2,
                (minY + maxY) / 2 - scale.get().getY() * height / 2
        ));
    }

    public void zoom(Point2D zoomOrigin, double kx, double ky) {
        scale.set(new Point2D(
                kx * scale.get().getX(),
                ky * scale.get().getY()
        ));
        origin.set(new Point2D(
                zoomOrigin.getX() - kx * (zoomOrigin.getX() - origin.get().getX()),
                zoomOrigin.getY() - ky * (zoomOrigin.getY() - origin.get().getY())
        ));
    }
}

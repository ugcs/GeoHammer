package com.ugcs.geohammer.util;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;

public final class Nodes {

    private Nodes() {
    }

    public static Bounds getBoundsInParent(Node node, Parent parent) {
        if (node == null)
            return null;
        Bounds local = node.getBoundsInLocal();
        double offsetX = 0;
        double offsetY = 0;
        while (node != null && node != parent) {
            offsetX += node.getLayoutX() + node.getTranslateX();
            offsetY += node.getLayoutY() + node.getTranslateY();
            node = node.getParent();
        }
        return new BoundingBox(offsetX, offsetY,
                local.getWidth(), local.getHeight());
    }
}

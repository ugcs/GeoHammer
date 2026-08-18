package com.ugcs.geohammer.view.control;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Listeners;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class NodeWithOverlay<T extends Region> extends StackPane {

    private static final double OVERLAY_MARGIN = 4;

    protected final T node;

    protected final Region overlay;

    public NodeWithOverlay(T node, Region overlay) {
        this.node = Check.notNull(node);
        this.overlay = Check.notNull(overlay);

        this.overlay.setFocusTraversable(false);

        StackPane.setAlignment(this.overlay, Pos.CENTER_RIGHT);
        StackPane.setMargin(this.overlay, new Insets(0, OVERLAY_MARGIN, 0, 0));

        Listeners.onChange(this.overlay.widthProperty(), width -> {
            if (width.doubleValue() <= 0) {
                return;
            }
            Insets padding = this.node.getPadding();
            this.node.setPadding(new Insets(
                    padding.getTop(),
                    width.doubleValue() + 2 * OVERLAY_MARGIN,
                    padding.getBottom(),
                    padding.getLeft()));
        });

        getChildren().addAll(this.node, this.overlay);
    }

    public T getNode() {
        return node;
    }

    public Region getOverlay() {
        return overlay;
    }
}

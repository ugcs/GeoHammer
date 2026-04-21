package com.ugcs.geohammer.view;

import com.ugcs.geohammer.util.Check;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.StackPane;
import org.jspecify.annotations.Nullable;

public class CanvasWindow extends UtilityWindow {

    @Nullable
    protected Canvas canvas;

    public CanvasWindow(WindowProperties properties) {
        super(properties);
    }

    public @Nullable Canvas getCanvas() {
        return canvas;
    }

    @Override
    protected void onCreate() {
        super.onCreate();

        StackPane parent = root;
        Check.notNull(parent);

        canvas = new Canvas();
        Listeners.onChange(canvas.widthProperty(), v -> onDraw());
        Listeners.onChange(canvas.heightProperty(), v -> onDraw());

        canvas.widthProperty().bind(parent.widthProperty());
        canvas.heightProperty().bind(parent.heightProperty());
        parent.getChildren().add(canvas);
    }

    protected void onDraw() {
    }
}

package com.ugcs.geohammer.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;

public class UtilityWindow {

    private static final double SCREEN_MARGIN = 100;

    private final WindowProperties properties;

    public UtilityWindow(WindowProperties properties) {
        this.properties = properties;
    }

    @Nullable
    protected Stage window;

    @Nullable
    protected StackPane root;

    public @Nullable Stage getWindow() {
        return window;
    }

    public @Nullable StackPane getRoot() {
        return root;
    }

    private Stage createWindow() {
        Stage owner = AppContext.stage;
        Stage window = new Stage();

        window.setTitle(properties.title());
        window.initOwner(owner);
        window.initStyle(properties.style());
        window.setResizable(true);

        root = new StackPane();
        root.setMinSize(0, 0);

        Scene scene = new Scene(root);
        window.setScene(scene);
        AppContext.getInstance(ThemeService.class).registerScene(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                hide();
            }
        });

        window.setOnCloseRequest(event -> {
            event.consume();
            hide();
        });

        Listeners.onChange(owner.focusedProperty(), window::setAlwaysOnTop);
        return window;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    private void fitScreen() {
        if (window == null) {
            return;
        }

        Window owner = window.getOwner();
        Rectangle2D screenBounds = Views.getScreen(owner).getVisualBounds();

        double width = Math.min(properties.width(), screenBounds.getWidth() - SCREEN_MARGIN);
        double height = Math.min(properties.height(), screenBounds.getHeight() - SCREEN_MARGIN);

        window.setWidth(width);
        window.setHeight(height);
        window.setMinWidth(Math.min(width, properties.minWidth()));
        window.setMinHeight(Math.min(height, properties.minHeight()));

        // center inside visual screen bounds
        window.setX(screenBounds.getMinX() + (screenBounds.getWidth() - width) / 2);
        window.setY(screenBounds.getMinY() + (screenBounds.getHeight() - height) / 2);
    }

    public void show() {
        Platform.runLater(() -> {
            if (window == null) {
                window = createWindow();
                onCreate();
            }
            fitScreen();
            window.show();
            window.toFront();
            onShow();
        });
    }

    public void hide() {
        if (window != null) {
            Platform.runLater(() -> {
                window.hide();
                onHide();
            });
        }
    }

    public void toggle() {
        if (isShowing()) {
            hide();
        } else {
            show();
        }
    }

    protected void onCreate() {
    }

    protected void onShow() {
    }

    protected void onHide() {
    }
}

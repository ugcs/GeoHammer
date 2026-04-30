package com.ugcs.geohammer.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.view.style.ThemeService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.Nullable;

public class UtilityWindow {

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
        Stage window = new Stage();

        window.setTitle(properties.title());
        window.initOwner(AppContext.stage);
        window.initStyle(StageStyle.UTILITY);
        window.setResizable(true);
        window.setMinWidth(properties.minWidth());
        window.setMinHeight(properties.minHeight());

        root = new StackPane();
        root.setMinSize(0, 0);

        Scene scene = new Scene(root, properties.width(), properties.height());
        window.setScene(scene);
        AppContext.getInstance(ThemeService.class).registerScene(scene);

        window.setOnCloseRequest(event -> {
            event.consume();
            hide();
        });
        Listeners.onChange(AppContext.stage.focusedProperty(), window::setAlwaysOnTop);
        return window;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    public void show() {
        Platform.runLater(() -> {
            if (window == null) {
                window = createWindow();
                onCreate();
            }
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

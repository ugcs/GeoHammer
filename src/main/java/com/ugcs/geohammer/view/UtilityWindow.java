package com.ugcs.geohammer.view;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.util.Strings;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jspecify.annotations.Nullable;

public class UtilityWindow {

    private static final double MIN_WIDTH = 600;

    private static final double MIN_HEIGHT = 400;

    private static final double INIT_WIDTH = 1200;

    private static final double INIT_HEIGHT = 840;

    private final String title;

    private final String style;

    public UtilityWindow(String title, String style) {
        this.title = title;
        this.style = style;
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

        window.setTitle(title);
        window.initOwner(AppContext.stage);
        window.initStyle(StageStyle.UTILITY);
        window.setResizable(true);
        window.setMinWidth(MIN_WIDTH);
        window.setMinHeight(MIN_HEIGHT);

        root = new StackPane();
        root.setMinSize(0, 0);

        Scene scene = new Scene(root, INIT_WIDTH, INIT_HEIGHT);
        if (!Strings.isNullOrEmpty(style)) {
            Styles.addResource(scene, style);
        }
        window.setScene(scene);

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

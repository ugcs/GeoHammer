package com.ugcs.geohammer.view.style;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Listeners;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ThemeService {

    private final Set<Scene> scenes = new HashSet<>();

    private Theme theme = Theme.DEFAULT;

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = Check.notNull(theme);
        Platform.runLater(() -> {
            scenes.forEach(scene -> applyTheme(scene, theme));
        });
    }

    private void applyTheme(Scene scene, Theme theme) {
        Check.notNull(scene);
        Check.notNull(theme);

        List<String> urls = theme.stylesheets().stream()
                .map(Styles::urlFromResource)
                .toList();
        scene.getStylesheets().setAll(urls);
    }

    private void bindWindow(Scene scene, Window window) {
        if (window == null) {
            return;
        }
        window.addEventHandler(WindowEvent.WINDOW_CLOSE_REQUEST,
                e -> unregisterScene(scene));
    }

    public void registerScene(Scene scene) {
        if (scene == null) {
            return;
        }
        Platform.runLater(() -> {
            if (!scenes.add(scene)) {
                return;
            }
            applyTheme(scene, theme);
            Window window = scene.getWindow();
            if (window != null) {
                bindWindow(scene, window);
            } else {
                Listeners.onChange(scene.windowProperty(), w -> bindWindow(scene, w));
            }
        });
    }

    public void unregisterScene(Scene scene) {
        if (scene == null) {
            return;
        }
        Platform.runLater(() -> {
            scenes.remove(scene);
        });
    }
}

package com.ugcs.geohammer.release;

import com.ugcs.geohammer.service.github.Release;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.view.Styles;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

@Component
public class ReleaseView extends Popup {

    private final ReleaseService releaseService;

    private final VBox content;

    private final ScrollPane scrollPane;

    public ReleaseView(ReleaseService releaseService) {
        this.releaseService = releaseService;

        content = new VBox();
        content.getStyleClass().add("release-rows");

        scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("release-scroll");
        Styles.addResource(scrollPane, Styles.STATUS_STYLE_PATH);

        // parent workaround container (to catch scroll pane focus)
        VBox container = new VBox(scrollPane);

        // redirect focus to parent
        scrollPane.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                container.requestFocus();
            }
        });

        getContent().add(container);
        setAutoHide(true);
    }

    public void showAbove(Node owner) {
        if (isShowing()) {
            hide();
            return;
        }

        // render off-screen so layout gets a size
        show(owner, -10_000, -10_000);

        Bounds ownerBounds = owner.localToScreen(owner.getBoundsInLocal());
        setX(ownerBounds.getMaxX() - getWidth());
        setY(ownerBounds.getMinY() - getHeight() - 1);
    }

    public void update() {
        setMessage("Loading...");
        releaseService.getReleases().whenComplete((releases, t) -> {
            Platform.runLater(() -> {
                if (t == null && !Nulls.isNullOrEmpty(releases)) {
                    setReleases(releases);
                } else {
                    setMessage("Not available");
                }
            });
        });
    }

    private void setMessage(String text) {
        content.getChildren().clear();
        content.getChildren().add(createMessageRow(text));
    }

    private void setReleases(List<Release> releases) {
        content.getChildren().clear();
        for (Release release : releases) {
            content.getChildren().add(createReleaseRow(release));
        }
    }

    private HBox createRowContainer() {
        HBox container = new HBox();
        container.getStyleClass().add("release-row-container");
        return container;
    }

    private HBox createMessageRow(String text) {
        HBox row = createRowContainer();
        Label message = new Label(text);
        row.getChildren().add(message);
        return row;
    }

    private HBox createReleaseRow(Release release) {
        HBox row = createRowContainer();
        row.getStyleClass().add("release-row");

        // version
        String releaseVersion = release.getBuildVersion();
        Label version = new Label(release.isPreRelease()
                ? releaseVersion + " (pre-release)"
                : releaseVersion);
        if (releaseService.isCurrent(release)) {
            version.getStyleClass().add("current-version");
        }
        if (release.isPreRelease()) {
            version.getStyleClass().add("pre-release");
        }
        row.getChildren().add(version);

        // spacer
        row.getChildren().add(Views.createSpacer());

        // download link
        Hyperlink download = new Hyperlink("Download");
        download.setOnAction(event -> openBrowser(release.getHtmlUrl()));
        // visible property affects layout changes so using opacity instead
        download.setOpacity(0);
        download.opacityProperty().bind(Bindings.when(row.hoverProperty())
                .then(1.0)
                .otherwise(0.0));
        row.getChildren().add(download);

        return row;
    }

    private void openBrowser(String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ignored) {
        }
    }
}

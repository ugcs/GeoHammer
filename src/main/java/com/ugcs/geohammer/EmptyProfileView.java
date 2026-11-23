package com.ugcs.geohammer;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.service.demo.DemoSurvey;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.input.DragEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.List;

@Component
public class EmptyProfileView extends VBox {

    private static final Logger log = LoggerFactory.getLogger(EmptyProfileView.class);

    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    private static final String WIKI_URL = "https://github.com/ugcs/GeoHammer/wiki";

    private static final double ICON_SIZE = 40;

    private final Model model;

    private final Loader loader;

    private final Hyperlink gprDemoLink = createDemoLink(DemoSurvey.GPR);

    private final Hyperlink csvDemoLink = createDemoLink(DemoSurvey.CSV);

    public EmptyProfileView(Model model, Loader loader) {
        Check.notNull(model);
        Check.notNull(loader);

        this.model = model;
        this.loader = loader;

        getStyleClass().add("empty-profile-view");
        setAlignment(Pos.CENTER);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        getChildren().addAll(createSpacer(), createDropZone(), createSpacer(), createLinks());

        addEventHandler(DragEvent.DRAG_ENTERED, event -> setDropTarget(event.getDragboard().hasFiles()));
        addEventHandler(DragEvent.DRAG_EXITED, event -> setDropTarget(false));
        addEventFilter(DragEvent.DRAG_DROPPED, event -> setDropTarget(false));
    }

    private void setDropTarget(boolean dropTarget) {
        pseudoClassStateChanged(DROP_TARGET, dropTarget);
    }

    private static Region createSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private VBox createDropZone() {
        Label hint = new Label("Drop files or folders here");
        hint.getStyleClass().addAll("empty-profile-title", "dim");
        Label note = new Label("Several files and whole folders can be opened at once");
        note.getStyleClass().add("dim");

        HBox demos = new HBox(gprDemoLink, csvDemoLink);
        demos.getStyleClass().add("empty-profile-demos");
        demos.setAlignment(Pos.CENTER);

        FlowPane groups = new FlowPane(
                createDataGroup(ResourceImageHolder.GPR_DATA,
                        "GPR radargrams (SEG-Y)", "GSSI radargrams (DZT)"),
                createDataGroup(ResourceImageHolder.SENSOR_DATA,
                        "Sensor tables (CSV, TXT, XYZ)", "GNSS positions (POS)"),
                createDataGroup(ResourceImageHolder.SONAR_DATA,
                        "GPS tracks (NMEA)", "Sonar logs (SVLOG)"));
        groups.getStyleClass().add("empty-profile-groups");
        groups.setAlignment(Pos.CENTER);
        // one row when there is room; a narrow chart area wraps whole groups instead of cutting their text
        groups.setPrefWrapLength(Double.MAX_VALUE);

        VBox box = new VBox(Views.DEFAULT_SPACING, createIcon(ResourceImageHolder.UPLOAD_FILE), hint, note, demos,
                groups);
        box.getStyleClass().add("empty-profile-drop-zone");
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // icon paths differ in height, so a fixed box keeps them centered
    private static StackPane createIcon(String iconPath) {
        SVGPath icon = new SVGPath();
        icon.setContent(iconPath);
        icon.getStyleClass().add("empty-profile-icon");
        StackPane iconBox = new StackPane(icon);
        iconBox.setMinSize(ICON_SIZE, ICON_SIZE);
        iconBox.setPrefSize(ICON_SIZE, ICON_SIZE);
        iconBox.setMaxSize(ICON_SIZE, ICON_SIZE);
        return iconBox;
    }

    private static HBox createDataGroup(String iconPath, String firstLine, String secondLine) {
        StackPane iconBox = createIcon(iconPath);

        Label first = new Label(firstLine);
        Label second = new Label(secondLine);
        first.getStyleClass().add("dim");
        second.getStyleClass().add("dim");
        VBox lines = new VBox(first, second);
        lines.setAlignment(Pos.CENTER_LEFT);

        HBox group = new HBox(iconBox, lines);
        group.getStyleClass().add("empty-profile-group");
        group.setAlignment(Pos.CENTER_LEFT);
        return group;
    }

    private Hyperlink createDemoLink(DemoSurvey demoSurvey) {
        Hyperlink link = new Hyperlink(demoSurvey.getTitle());
        link.getStyleClass().add("empty-profile-demo");
        link.setOnAction(event -> openDemo(demoSurvey));
        return link;
    }

    private Node createLinks() {
        HBox links = new HBox(Views.DEFAULT_SPACING,
                createLink("Quick Start", WIKI_URL + "/Quick-Start"),
                createLink("Templates", WIKI_URL + "/Template-Format-Specification"),
                createLink("AI integration", WIKI_URL + "/AI-Integration"));
        links.setAlignment(Pos.CENTER);
        return links;
    }

    private Hyperlink createLink(String text, String url) {
        Hyperlink link = new Hyperlink(text);
        link.getStyleClass().add("empty-profile-wiki-link");
        link.setOnAction(event -> Views.browse(url));
        return link;
    }

    private void openDemo(DemoSurvey demoSurvey) {
        File file;
        try {
            file = demoSurvey.getFile();
        } catch (NoSuchFileException e) {
            log.error("Cannot open {}", demoSurvey.getTitle(), e);
            Dialogs.showError("Can't open " + demoSurvey.getTitle(), e);
            return;
        }
        loader.load(List.of(file));
    }

    private void updateVisibility() {
        Platform.runLater(() -> setVisible(model.getFileManager().getFilesCount() == 0));
    }

    @EventListener
    private void onFileOpened(FileOpenedEvent event) {
        updateVisibility();
    }

    @EventListener
    private void onFileClosed(FileClosedEvent event) {
        updateVisibility();
    }
}

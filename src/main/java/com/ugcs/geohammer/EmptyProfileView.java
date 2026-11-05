package com.ugcs.geohammer;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.FileClosedEvent;
import com.ugcs.geohammer.model.event.FileOpenedEvent;
import com.ugcs.geohammer.service.demo.DemoFile;
import com.ugcs.geohammer.service.demo.DemoService;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.FileTypes;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

@Component
public class EmptyProfileView extends VBox {

    private static final Logger log = LoggerFactory.getLogger(EmptyProfileView.class);

    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    private static final String WIKI_URL = "https://github.com/ugcs/GeoHammer/wiki";

    private static final double COLUMN_WIDTH = 300;

    private static final double UPLOAD_ICON_SIZE = 40;

    private static final String PROBE_FILE_NAME = "file";

    private final Model model;

    private final DemoService demoService;

    private final Button gprDemoButton = createDemoButton(DemoFile.GPR);

    private final Button csvDemoButton = createDemoButton(DemoFile.CSV);

    public EmptyProfileView(Model model, DemoService demoService) {
        Check.notNull(model);
        Check.notNull(demoService);

        this.model = model;
        this.demoService = demoService;

        getStyleClass().add("empty-profile-view");
        setAlignment(Pos.CENTER);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        List<String> gprExtensions = new ArrayList<>();
        List<String> dataExtensions = new ArrayList<>();
        for (String extension : FileTypes.getSupportedExtensions()) {
            if (isGprExtension(extension)) {
                gprExtensions.add(extension);
            } else {
                dataExtensions.add(extension);
            }
        }
        HBox columns = new HBox(
                createColumn("GPR data", gprExtensions, gprDemoButton, "Gas pipes, drone GPR survey"),
                new Separator(Orientation.VERTICAL),
                createColumn("Sensor data", dataExtensions, csvDemoButton, "Magnetic anomalies, drone MagNIMBUS survey"));
        columns.getStyleClass().add("empty-profile-columns");
        columns.setMaxWidth(Region.USE_PREF_SIZE);

        VBox openHint = createOpenHint();
        openHint.maxWidthProperty().bind(columns.widthProperty());

        // equal spacers keep the demo columns halfway between the drop zone and the links at the bottom
        getChildren().addAll(createSpacer(), openHint, createSpacer(), columns, createSpacer(), createLinks());

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

    private VBox createOpenHint() {
        Label hint = new Label("Drop files or folders here");
        hint.getStyleClass().add("empty-profile-title");
        Label note = new Label("Several files and whole folders can be opened at once");
        note.getStyleClass().add("dim");

        VBox box = new VBox(Views.DEFAULT_SPACING);
        ImageView uploadImage = ResourceImageHolder.getImageView("upload_file.png");
        if (uploadImage != null) {
            uploadImage.setFitWidth(UPLOAD_ICON_SIZE);
            uploadImage.setFitHeight(UPLOAD_ICON_SIZE);
            Views.tintImage(uploadImage, Color.GRAY);
            box.getChildren().add(uploadImage);
        }
        box.getChildren().addAll(hint, note);
        box.getStyleClass().add("empty-profile-drop-zone");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private VBox createColumn(String title, List<String> fileExtensions, Button demoButton, String demoCaption) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-profile-column-title");

        FlowPane extensions = new FlowPane();
        extensions.getStyleClass().add("empty-profile-formats");
        extensions.setAlignment(Pos.CENTER);
        extensions.setPrefWrapLength(COLUMN_WIDTH);
        for (String extension : fileExtensions) {
            Label tag = new Label("." + extension);
            tag.getStyleClass().add("empty-profile-format");
            extensions.getChildren().add(tag);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label caption = new Label(demoCaption);
        caption.setWrapText(true);
        caption.setTextAlignment(TextAlignment.CENTER);
        caption.getStyleClass().addAll("dim", "smaller");

        VBox column = new VBox(Views.DEFAULT_SPACING, titleLabel, extensions, spacer, demoButton, caption);
        column.setAlignment(Pos.TOP_CENTER);
        column.setPrefWidth(COLUMN_WIDTH);
        return column;
    }

    private static boolean isGprExtension(String extension) {
        File file = new File(PROBE_FILE_NAME + "." + extension);
        return FileTypes.isGprFile(file) || FileTypes.isDztFile(file);
    }

    private Button createDemoButton(DemoFile demoFile) {
        Button button = new Button("Open " + demoFile.getTitle());
        button.setOnAction(event -> onDemo(demoFile));
        return button;
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
        link.setOnAction(event -> Views.browse(url));
        return link;
    }

    private void onDemo(DemoFile demoFile) {
        setDemoButtonsDisabled(true);
        demoService.openDemo(demoFile).whenComplete((result, error) -> {
            Platform.runLater(() -> setDemoButtonsDisabled(false));
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause()
                        : error;
                log.error("Cannot open {}", demoFile.getTitle(), cause);
                Dialogs.showError("Can't open " + demoFile.getTitle(), cause);
            }
        });
    }

    private void setDemoButtonsDisabled(boolean disabled) {
        gprDemoButton.setDisable(disabled);
        csvDemoButton.setDisable(disabled);
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

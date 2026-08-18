package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.template.TemplateEditorController;
import com.ugcs.geohammer.template.model.FileSample;
import com.ugcs.geohammer.template.model.FormatModel;
import com.ugcs.geohammer.template.model.TemplateModel;
import com.ugcs.geohammer.template.model.TimeReference;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.Dialogs;
import com.ugcs.geohammer.view.Views;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
public class FormatView extends VBox {

    private static final int LABEL_WIDTH = 76;

    private final TemplateModel templateModel;

    private final TemplateEditorController templateEditorController;

    public FormatView(TemplateModel templateModel,
                      TemplateEditorController templateEditorController,
                      SeparatorsView separatorsView) {
        this.templateModel = templateModel;
        this.templateEditorController = templateEditorController;

        TextField nameInput = new TextField();
        nameInput.setMinWidth(100);
        nameInput.setPrefWidth(350);
        nameInput.textProperty().bindBidirectional(templateModel.nameProperty());

        Button saveAs = new Button("Save template…");
        saveAs.setMinWidth(Region.USE_PREF_SIZE);
        saveAs.setOnAction(e -> saveTemplateAs());
        saveAs.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !templateEditorController.validate().getMessages().isEmpty(),
                templateEditorController.validationDependencies()));

        FormatModel format = templateModel.getFormat();

        TextField commentInput = new TextField();
        commentInput.setPrefWidth(76);
        commentInput.setMinWidth(76);
        commentInput.textProperty().bindBidirectional(format.commentPrefixProperty());

        CheckBox hasHeader = new CheckBox("Has header");
        hasHeader.selectedProperty().bindBidirectional(templateModel.getFormat().hasHeaderProperty());

        CheckBox reorderByTime = new CheckBox("Reorder by time");
        reorderByTime.selectedProperty().bindBidirectional(templateModel.reorderByTimeProperty());

        ComboBox<TimeReference> timeReferenceSelector = new ComboBox<>(FXCollections.observableArrayList(
                List.of(TimeReference.values())));
        timeReferenceSelector.valueProperty().bindBidirectional(templateModel.timeReferenceProperty());

        setSpacing(TemplateEditorView.VERTICAL_SPACING);
        getChildren().addAll(
                createGroup(
                        Views.createFixedLabel("Template", LABEL_WIDTH),
                        nameInput,
                        Views.createSpacer(),
                        saveAs
                ),
                new Separator(Orientation.HORIZONTAL),
                createGroup(
                        Views.createFixedLabel("Separator", LABEL_WIDTH),
                        separatorsView
                ),
                createGroup(
                        Views.createFixedLabel("Comment", LABEL_WIDTH),
                        commentInput,
                        hasHeader,
                        Views.createSpacer(),
                        reorderByTime,
                        timeReferenceSelector
                ));
    }

    private HBox createGroup(Node... nodes) {
        HBox group = new HBox(TemplateEditorView.HORIZONTAL_SPACING, nodes);
        group.setAlignment(Pos.CENTER_LEFT);
        return group;
    }

    // templates are saved next to the sample file
    private void saveTemplateAs() {
        FileChooser fileSelector = new FileChooser();
        fileSelector.setTitle("Save template");
        fileSelector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Template files (*.yaml)", "*.yaml"));

        FileSample fileSample = templateModel.getFileSample();
        File directory = fileSample != null ? fileSample.file().getParentFile() : null;
        if (directory != null && directory.isDirectory()) {
            fileSelector.setInitialDirectory(directory);
        }
        String name = Strings.trim(templateModel.getName());
        if (!Strings.isNullOrEmpty(name)) {
            fileSelector.setInitialFileName(name + ".yaml");
        }

        File selected = fileSelector.showSaveDialog(getScene().getWindow());
        if (selected == null) {
            return;
        }
        try {
            templateEditorController.saveTemplate(selected);
        } catch (IOException e) {
            Dialogs.showError("Cannot save template", e);
        }
    }
}

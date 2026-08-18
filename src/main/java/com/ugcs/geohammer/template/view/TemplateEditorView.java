package com.ugcs.geohammer.template.view;

import com.ugcs.geohammer.template.TemplateEditorController;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.view.UtilityWindow;
import com.ugcs.geohammer.view.Views;
import com.ugcs.geohammer.view.WindowProperties;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import org.controlsfx.validation.ValidationMessage;
import org.controlsfx.validation.ValidationResult;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TemplateEditorView extends UtilityWindow {

    public static final double VERTICAL_SPACING = 8;

    public static final double HORIZONTAL_SPACING = 8;

    private final TemplateEditorController templateEditorController;

    private final FormatView formatView;

    private final SampleView sampleView;

    @Nullable
    private CompletableFuture<@Nullable Template> result;

    public TemplateEditorView(
            TemplateEditorController templateEditorController,
            FormatView formatView,
            SampleView sampleView) {
        super(getWindowProperties());

        this.templateEditorController = templateEditorController;
        this.formatView = formatView;
        this.sampleView = sampleView;
    }

    private static WindowProperties getWindowProperties() {
        return new WindowProperties("Template Editor")
                .withStyle(StageStyle.DECORATED)
                .withSize(720, 480)
                .withMinSize(640, 400);
    }

    public void show(CompletableFuture<@Nullable Template> result) {
        this.result = result;
        show();
    }

    @Override
    protected void onCreate() {
        if (root == null) {
            return;
        }
        root.getStyleClass().add("surface");

        Node content = createContent();
        Node bottomBar = createBottomBar();
        VBox container = new VBox(VERTICAL_SPACING, content, bottomBar);
        VBox.setVgrow(content, Priority.ALWAYS);

        container.setPadding(new Insets(12));
        root.getChildren().add(container);
    }

    @Override
    protected void onHide() {
        if (result != null) {
            result.complete(null);
        }
    }

    private Node createContent() {
        VBox content = new VBox(VERTICAL_SPACING,
                formatView,
                sampleView);
        VBox.setVgrow(sampleView, Priority.ALWAYS);
        return content;
    }

    private Node createBottomBar() {
        Label status = new Label();
        status.textProperty().bind(Bindings.createStringBinding(
                this::getStatusMessage,
                templateEditorController.validationDependencies()));

        Button cancel = new Button("Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> hide());

        Button useTemplate = new Button("Use template");
        useTemplate.setDefaultButton(true);
        useTemplate.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !templateEditorController.validate().getMessages().isEmpty(),
                templateEditorController.validationDependencies()));
        useTemplate.setOnAction(e -> useTemplate());

        HBox bottomBar = new HBox(Views.DEFAULT_SPACING,
                status,
                Views.createSpacer(),
                cancel,
                useTemplate);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        return bottomBar;
    }

    private String getStatusMessage() {
        ValidationResult validation = templateEditorController.validate();
        for (ValidationMessage message : validation.getMessages()) {
            return message.getText();
        }
        return Strings.empty();
    }

    private void useTemplate() {
        if (result != null) {
            result.complete(templateEditorController.buildTemplate());
        }
        hide();
    }
}

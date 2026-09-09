package com.ugcs.geohammer.feedback;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class FeedbackView extends VBox {

    private final TextField name = new TextField();

    private final TextField email = new TextField();

    private final TextField subject = new TextField();

    private final TextArea message = new TextArea();

    private final CheckBox attachScreenshot = new CheckBox("Attach screenshot");

    private final CheckBox attachFiles = new CheckBox("Attach open files");

    public FeedbackView() {
        this(null, null);
    }

    public FeedbackView(String subjectText, String messageText) {
        setSpacing(8);

        subject.setText(subjectText);
        message.setText(messageText);

        message.setPrefRowCount(8);
        message.setWrapText(true);
        VBox.setVgrow(message, Priority.ALWAYS);

        attachScreenshot.setSelected(true);
        attachFiles.setSelected(true);

        getChildren().addAll(
                createInputField("Name", name),
                createInputField("Email", email),
                createInputField("Subject", subject),
                message,
                attachScreenshot,
                attachFiles
        );

        Settings settings = AppContext.getInstance(Settings.class);
        loadSettings(settings);
    }

    private Node createInputField(String labelText, TextField input) {
        Label label = new Label(labelText);
        label.setPrefWidth(60);
        label.setMinWidth(60);
        HBox.setHgrow(input, Priority.ALWAYS);
        HBox container = new HBox(label, input);
        container.setAlignment(Pos.BASELINE_LEFT);
        return container;
    }

    private void loadSettings(Settings settings) {
        if (Strings.isNullOrEmpty(name.getText())) {
            name.setText(settings.getStringOrDefault(
                    "feedback", "name", Strings.empty()));
        }
        if (Strings.isNullOrEmpty(email.getText())) {
            email.setText(settings.getStringOrDefault(
                    "feedback", "email", Strings.empty()));
        }
    }

    private void saveSettings(Settings settings) {
        if (!Strings.isNullOrEmpty(name.getText())) {
            settings.setValue("feedback", "name", name.getText());
        }
        if (!Strings.isNullOrEmpty(email.getText())) {
            settings.setValue("feedback", "email", email.getText());
        }
    }

    public boolean validate() {
        if (Strings.isNullOrEmpty(name.getText())) {
            name.requestFocus();
            return false;
        }
        if (Strings.isNullOrEmpty(email.getText())) {
            email.requestFocus();
            return false;
        }
        if (Strings.isNullOrEmpty(subject.getText())) {
            subject.requestFocus();
            return false;
        }
        return true;
    }

    public void submit() {
        submit(List.of());
    }

    public void submit(List<Attachment> attachments) {
        Settings settings = AppContext.getInstance(Settings.class);
        saveSettings(settings);

        FeedbackService feedbackService = AppContext.getInstance(FeedbackService.class);
        Feedback feedback = new Feedback(
                name.getText(),
                email.getText(),
                subject.getText(),
                message.getText());

        // collect attachments
        List<Attachment> allAttachments = new ArrayList<>();
        if (attachScreenshot.isSelected()) {
            allAttachments.addAll(feedbackService.createScreenshotAttachments());
        }
        if (attachFiles.isSelected()) {
            // include extra attachments only when attach files is checked
            if (!Nulls.isNullOrEmpty(attachments)) {
                allAttachments.addAll(attachments);
            }
            allAttachments.addAll(feedbackService.createOpenFileAttachments());
        }
        feedbackService.submitFeedback(feedback, allAttachments);
    }
}

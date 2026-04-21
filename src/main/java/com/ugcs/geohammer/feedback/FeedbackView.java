package com.ugcs.geohammer.feedback;

import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.PrefSettings;
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

        PrefSettings preferences = AppContext.getInstance(PrefSettings.class);
        loadPreferences(preferences);
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

    private void loadPreferences(PrefSettings preferences) {
        if (Strings.isNullOrEmpty(name.getText())) {
            name.setText(preferences.getStringOrDefault(
                    "feedback", "name", Strings.empty()));
        }
        if (Strings.isNullOrEmpty(email.getText())) {
            email.setText(preferences.getStringOrDefault(
                    "feedback", "email", Strings.empty()));
        }
    }

    private void savePreferences(PrefSettings preferences) {
        if (!Strings.isNullOrEmpty(name.getText())) {
            preferences.setValue("feedback", "name", name.getText());
        }
        if (!Strings.isNullOrEmpty(email.getText())) {
            preferences.setValue("feedback", "email", email.getText());
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
        PrefSettings preferences = AppContext.getInstance(PrefSettings.class);
        savePreferences(preferences);

        Feedback feedback = new Feedback(
                name.getText(),
                email.getText(),
                subject.getText(),
                message.getText());

        FeedbackService feedbackService = AppContext.getInstance(FeedbackService.class);
        feedbackService.submitFeedback(feedback, attachScreenshot.isSelected(), attachFiles.isSelected());
    }
}

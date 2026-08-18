package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.settings.AgentController.Status;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AgentView extends VBox {

    private final AgentController agentController;

    private final Button agentStatus;

    public AgentView(AgentController agentController) {
        this.agentController = Check.notNull(agentController);

        agentStatus = new Button("Enable");
        agentStatus.setPrefWidth(80);
        agentStatus.setOnAction(event -> toggleAgentStatus());

        Label copyHint = new Label("Enable manually in terminal");
        copyHint.getStyleClass().add("dim");

        Button copy = Views.createSvgButton(
                ResourceImageHolder.COPY,
                24,
                "Copy command to clipboard");
        copy.setOnAction(event -> copyToClipboard(agentController.getEnableCommand()));

        HBox agentStatusRow = new HBox(Views.DEFAULT_SPACING, agentStatus, Views.createSpacer(), copyHint, copy);
        agentStatusRow.setAlignment(Pos.CENTER_LEFT);

        setSpacing(SettingsView.VERTICAL_SPACING);
        getChildren().addAll(
                Views.createLabeledSeparator(agentController.getName() + " Integration"),
                agentStatusRow);

        Listeners.onChange(agentController.statusProperty(), status -> updateAgentStatus());
        Listeners.onChange(agentController.busyProperty(), busy -> updateAgentStatus());

        updateAgentStatus();
    }

    private void toggleAgentStatus() {
        Status status = agentController.statusProperty().get();
        if (status == Status.DISABLED) {
            agentController.enable();
        }
        if (status == Status.ENABLED) {
            agentController.disable();
        }
    }

    private void updateAgentStatus() {
        Status status = agentController.statusProperty().get();
        boolean busy = agentController.busyProperty().get();

        String text = switch(status) {
            case DISABLED -> "Enable";
            case ENABLED -> "Disable";
            default -> "Not available";
        };
        if (busy) {
            text = "Working";
        }
        agentStatus.setText(text);
        agentStatus.setDisable(busy || status == Status.UNKNOWN || status == Status.NOT_AVAILABLE);
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}

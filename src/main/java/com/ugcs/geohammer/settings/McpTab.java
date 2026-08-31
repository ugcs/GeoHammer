package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.mcp.McpServer;
import com.ugcs.geohammer.service.script.CommandExecutor;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.ResourceImageHolder;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class McpTab implements SettingsTab {

    private static final String DOCUMENTATION_URL =
            "https://github.com/ugcs/GeoHammer/wiki/AI-Integration";

    private final McpServer mcpServer;

    private final List<AgentController> agents;

    private final VBox content;

    private final CheckBox mcpEnabled;

    public McpTab(McpServer mcpServer, CommandExecutor commandExecutor, ExecutorService executor) {
        this.mcpServer = Check.notNull(mcpServer);

        McpIdentity mcpIdentity = mcpServer.getIdentity();
        Label serverUrlLabel = new Label(mcpIdentity.url());

        serverUrlLabel.getStyleClass().add("dim");

        Button copyUrl = Views.createSvgButton(
                ResourceImageHolder.COPY,
                24,
                "Copy server address to clipboard");
        copyUrl.setOnAction(event -> Views.copyToClipboard(mcpIdentity.url()));

        // server is started and stopped on save, as any other setting
        mcpEnabled = new CheckBox("Enable MCP server");

        HBox serverRow = new HBox(Views.DEFAULT_SPACING,
                mcpEnabled,
                Views.createSpacer(),
                serverUrlLabel,
                copyUrl);
        serverRow.setAlignment(Pos.CENTER_LEFT);

        agents = List.of(
                new ClaudeAgent(mcpIdentity, commandExecutor, executor),
                new CodexAgent(mcpIdentity, commandExecutor, executor));

        content = new VBox(SettingsView.VERTICAL_SPACING, serverRow);
        for (AgentController agent : agents) {
            AgentView agentView = new AgentView(agent);
            content.getChildren().add(agentView);
        }

        Hyperlink helpLink = new Hyperlink("How to enable AI integration");
        ResourceImageHolder.setButtonImage(
                ResourceImageHolder.EXTERNAL_LINK,
                22,
                helpLink);
        // icon trails the text, as an external link marker
        helpLink.setContentDisplay(ContentDisplay.RIGHT);
        helpLink.setOnAction(event -> Views.browse(DOCUMENTATION_URL));

        content.getChildren().addAll(
                Views.createSpacer(),
                helpLink);
    }

    @Override
    public Tab getTab() {
        return new Tab("AI Integration", content);
    }

    @Override
    public void load() {
        mcpEnabled.setSelected(mcpServer.isEnabled());
        for (AgentController agent : agents) {
            agent.check();
        }
    }

    @Override
    public void save() {
        mcpServer.setEnabled(mcpEnabled.isSelected());
    }
}

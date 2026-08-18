package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.mcp.McpServer;
import com.ugcs.geohammer.service.script.CommandExecutor;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.view.Listeners;
import com.ugcs.geohammer.view.Views;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;

@Component
public class McpTab implements SettingsTab {

    private final McpServer mcpServer;

    private final List<AgentController> agents;

    private final VBox content;

    private final CheckBox mcpEnabled;

    public McpTab(McpServer mcpServer, CommandExecutor commandExecutor, ExecutorService executor) {
        this.mcpServer = Check.notNull(mcpServer);

        McpIdentity mcpIdentity = mcpServer.getIdentity();
        Label serverUrlLabel = new Label(mcpIdentity.url());

        serverUrlLabel.getStyleClass().add("dim");

        mcpEnabled = new CheckBox("Enable MCP server");
        // start/stop MCP server on checkbox changes
        Listeners.onChange(mcpEnabled.selectedProperty(), mcpServer::setEnabled);

        HBox serverRow = new HBox(Views.DEFAULT_SPACING,
                mcpEnabled,
                Views.createSpacer(),
                serverUrlLabel);
        serverRow.setAlignment(Pos.CENTER_LEFT);

        agents = List.of(
                new ClaudeAgent(mcpIdentity, commandExecutor, executor),
                new CodexAgent(mcpIdentity, commandExecutor, executor));

        content = new VBox(SettingsView.VERTICAL_SPACING, serverRow);
        for (AgentController agent : agents) {
            AgentView agentView = new AgentView(agent);
            content.getChildren().add(agentView);
        }
        Label agentRestartHint = new Label("Changes take effect in new agent sessions.");
        agentRestartHint.getStyleClass().add("dim");
        content.getChildren().add(agentRestartHint);
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
}

package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.service.script.CommandExecutor;

import java.util.concurrent.ExecutorService;

public class CodexAgent extends AgentController {

    public CodexAgent(McpIdentity server, CommandExecutor commandExecutor, ExecutorService executor) {
        super(server, commandExecutor, executor);
    }

    @Override
    public String getName() {
        return "Codex";
    }

    @Override
    public String getEnableCommand() {
        return "codex mcp add " + server.name() + " --url " + server.url();
    }

    @Override
    public String getDisableCommand() {
        return "codex mcp remove " + server.name();
    }

    @Override
    public String getCheckCommand() {
        return "codex mcp get " + server.name();
    }
}

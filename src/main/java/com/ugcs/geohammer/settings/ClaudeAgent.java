package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.service.script.CommandExecutor;

import java.util.concurrent.ExecutorService;

public class ClaudeAgent extends AgentController {

    public ClaudeAgent(McpIdentity server, CommandExecutor commandExecutor, ExecutorService executor) {
        super(server, commandExecutor, executor);
    }

    @Override
    public String getName() {
        return "Claude Code";
    }

    @Override
    public String getEnableCommand() {
        return "claude mcp add -t http -s user " + server.name() + " " + server.url();
    }

    @Override
    public String getDisableCommand() {
        return "claude mcp remove -s user " + server.name();
    }

    @Override
    public String getCheckCommand() {
        return "claude mcp get " + server.name();
    }
}

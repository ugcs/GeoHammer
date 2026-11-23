package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.service.script.CommandExecutor;

import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class ClaudeAgent extends AgentController {

    private static final Path HOME = Path.of(System.getProperty("user.home"));

    private static final List<Path> LOOKUP_PATHS = List.of(
            HOME.resolve(".local/bin"),
            HOME.resolve(".claude/local"),
            Path.of("/opt/homebrew/bin"),
            Path.of("/usr/local/bin"),
            HOME.resolve(".npm-global/bin"),
            HOME.resolve("AppData/Roaming/npm"),
            HOME.resolve("AppData/Local/Microsoft/WinGet/Links"));

    public ClaudeAgent(McpIdentity server, CommandExecutor commandExecutor, ExecutorService executor) {
        super(server, commandExecutor, executor);
    }

    @Override
    public String getName() {
        return "Claude Code";
    }

    @Override
    public String getExecutableName() {
        return "claude";
    }

    @Override
    public @Nullable Path resolveExecutable() {
        Path path = super.resolveExecutable();
        if (path != null) {
            return path;
        }
        return OperatingSystemUtils.findExecutable(getExecutableName(), LOOKUP_PATHS);
    }

    @Override
    public String getEnableArguments() {
        return "mcp add -t http -s user " + server.name() + " " + server.url();
    }

    @Override
    public String getDisableArguments() {
        return "mcp remove -s user " + server.name();
    }

    @Override
    public String getCheckArguments() {
        return "mcp get " + server.name();
    }
}

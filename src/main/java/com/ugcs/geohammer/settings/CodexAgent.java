package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.service.script.CommandExecutor;

import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class CodexAgent extends AgentController {

    private static final Path HOME = Path.of(System.getProperty("user.home"));

    private static final List<Path> LOOKUP_PATHS = List.of(
            HOME.resolve(".local/bin"),
            Path.of("/opt/homebrew/bin"),
            Path.of("/usr/local/bin"),
            HOME.resolve(".npm-global/bin"),
            HOME.resolve("AppData/Roaming/npm"));

    public CodexAgent(McpIdentity server, CommandExecutor commandExecutor, ExecutorService executor) {
        super(server, commandExecutor, executor);
    }

    @Override
    public String getName() {
        return "Codex";
    }

    @Override
    public String getExecutableName() {
        return "codex";
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
        return "mcp add " + server.name() + " --url " + server.url();
    }

    @Override
    public String getDisableArguments() {
        return "mcp remove " + server.name();
    }

    @Override
    public String getCheckArguments() {
        return "mcp get " + server.name();
    }
}

package com.ugcs.geohammer.settings;

import com.ugcs.geohammer.mcp.McpIdentity;
import com.ugcs.geohammer.service.script.CommandExecutionException;
import com.ugcs.geohammer.service.script.CommandExecutor;
import com.ugcs.geohammer.service.script.CommandResult;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Unchecked;
import com.ugcs.geohammer.view.Dialogs;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

public abstract class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final CommandExecutor commandExecutor;

    private final ExecutorService executor;

    protected final McpIdentity server;

    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNKNOWN);

    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    protected AgentController(McpIdentity server, CommandExecutor commandExecutor, ExecutorService executor) {
        this.server = Check.notNull(server);
        this.commandExecutor = Check.notNull(commandExecutor);
        this.executor = Check.notNull(executor);
    }

    public abstract String getName();

    public abstract String getCheckCommand();

    public abstract String getEnableCommand();

    public abstract String getDisableCommand();

    public ReadOnlyObjectProperty<Status> statusProperty() {
        return status;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }

    // run through a shell: resolves the CLI on a terminal-like PATH
    private static List<String> toShellCommand(String command) {
        if (OperatingSystemUtils.isWindows()) {
            return List.of("cmd", "/c", command);
        }
        String shell = System.getenv("SHELL");
        if (Strings.isNullOrEmpty(shell)) {
            shell = "/bin/sh";
        }
        return List.of(shell, "-lc", command);
    }

    private CommandResult runCommand(String command) throws InterruptedException {
        StringBuilder out = new StringBuilder();
        try {
            commandExecutor.executeCommand(toShellCommand(command), line -> {
                log.debug(line);
                out.append(line).append("\n");
            });
            return new CommandResult(CommandResult.EXIT_SUCCESS, out.toString());
        } catch (CommandExecutionException e) {
            return new CommandResult(e.getExitCode(), out.toString());
        } catch (IOException e) {
            log.debug("Command error", e);
            return new CommandResult(CommandResult.EXIT_UNKNOWN, out.toString());
        }
    }

    private boolean runEnable() throws InterruptedException {
        CommandResult result = runCommand(getEnableCommand());
        if (!result.isSuccess()) {
            Dialogs.showError(
                    "Error enabling " + getName(),
                    result.output());
        }
        return result.isSuccess();
    }

    private boolean runDisable() throws InterruptedException {
        CommandResult result = runCommand(getDisableCommand());
        if (!result.isSuccess()) {
            Dialogs.showError(
                    "Error disabling " + getName(),
                    result.output());
        }
        return result.isSuccess();
    }

    private Status runCheck() throws InterruptedException {
        CommandResult result = runCommand(getCheckCommand());
        if (result.isSuccess()) {
            return Status.ENABLED;
        }
        return result.isUnknown() || result.isNotFound()
                ? Status.NOT_AVAILABLE
                : Status.DISABLED;
    }

    private void submit(Unchecked.CheckedVoidCall task) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        executor.submit(() -> {
            try {
                task.invoke();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Error", e);
            } finally {
                Platform.runLater(() -> busy.set(false));
            }
        });
    }

    public void check() {
        submit(() -> {
            Status status = runCheck();
            Platform.runLater(() -> this.status.set(status));
        });
    }

    public void enable() {
        if (status.get() != Status.DISABLED) {
            return;
        }
        submit(() -> {
            if (runEnable()) {
                Status status = runCheck();
                Platform.runLater(() -> this.status.set(status));
            }
        });
    }

    public void disable() {
        if (status.get() != Status.ENABLED) {
            return;
        }
        submit(() -> {
            if (runDisable()) {
                Status status = runCheck();
                Platform.runLater(() -> this.status.set(status));
            }
        });
    }

    public enum Status {
        UNKNOWN,
        NOT_AVAILABLE,
        ENABLED,
        DISABLED
    }
}

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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

public abstract class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final CommandExecutor commandExecutor;

    private final ExecutorService executor;

    protected final McpIdentity server;

    private final ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.UNKNOWN);

    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private @Nullable Path executable;

    protected AgentController(McpIdentity server, CommandExecutor commandExecutor, ExecutorService executor) {
        this.server = Check.notNull(server);
        this.commandExecutor = Check.notNull(commandExecutor);
        this.executor = Check.notNull(executor);
    }

    public abstract String getName();

    public abstract String getExecutableName();

    public @Nullable Path resolveExecutable() {
        try {
            return OperatingSystemUtils.resolveExecutable(getExecutableName());
        } catch (IOException e) {
            log.warn("Cannot resolve executable {}", getExecutableName(), e);
            return null;
        }
    }

    public abstract String getCheckArguments();

    public abstract String getEnableArguments();

    public abstract String getDisableArguments();

    public String getEnableCommand() {
        return getExecutableName() + " " + getEnableArguments();
    }

    public ReadOnlyObjectProperty<Status> statusProperty() {
        return status;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }

    private CommandResult runAgentCommand(String arguments) throws InterruptedException {
        if (executable == null) {
            executable = resolveExecutable();
        }
        CommandResult result = runCommand(executable, arguments);
        if (result.isUnknown() || result.isNotFound()) {
            // the executable may be moved or removed: resolve again on the next command
            executable = null;
        }
        return result;
    }

    private CommandResult runCommand(@Nullable Path executable, String arguments) throws InterruptedException {
        if (executable == null) {
            return new CommandResult(CommandResult.EXIT_UNKNOWN, "Not found");
        }
        String command = executable.toString();
        if (command.contains(" ")) {
            command = "\"" + command + "\"";
        }
        if (!Strings.isNullOrBlank(arguments)) {
            command += " " + arguments;
        }
        StringBuilder out = new StringBuilder();
        try {
            commandExecutor.executeCommand(OperatingSystemUtils.toShellCommand(command), line -> {
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
        CommandResult result = runAgentCommand(getEnableArguments());
        if (!result.isSuccess()) {
            Dialogs.showError(
                    "Error enabling " + getName(),
                    result.output());
        }
        return result.isSuccess();
    }

    private boolean runDisable() throws InterruptedException {
        CommandResult result = runAgentCommand(getDisableArguments());
        if (!result.isSuccess()) {
            Dialogs.showError(
                    "Error disabling " + getName(),
                    result.output());
        }
        return result.isSuccess();
    }

    private Status runCheck() throws InterruptedException {
        CommandResult result = runAgentCommand(getCheckArguments());
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

package com.ugcs.geohammer.service.script;

public class CommandExecutionException extends RuntimeException {

    private final int exitCode;

    public CommandExecutionException(int exitCode) {
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}

package com.ugcs.geohammer.service.script;

public class ScriptException extends RuntimeException {

    private final int exitCode;

    public ScriptException(int exitCode) {
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }
}

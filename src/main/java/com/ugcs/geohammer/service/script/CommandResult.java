package com.ugcs.geohammer.service.script;

public record CommandResult(int exitCode, String output) {

    public static final int EXIT_UNKNOWN = -1;

    public static final int EXIT_SUCCESS = 0;

    public boolean isUnknown() {
        return exitCode == EXIT_UNKNOWN;
    }

    public boolean isSuccess() {
        return exitCode == EXIT_SUCCESS;
    }

    public boolean isNotFound() {
        //  127: shell
        // 9009: cmd
        return exitCode == 127 || exitCode == 9009;
    }
}

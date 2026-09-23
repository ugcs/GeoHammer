package com.ugcs.geohammer.mcp;

import org.jspecify.annotations.Nullable;

import java.io.File;

public class FileModifiedException extends IllegalStateException {

    public FileModifiedException(@Nullable File file) {
        super("File was modified by another client or in the app since it was last read, "
                + "read it again before writing"
                + (file != null ? ": " + file.getName() : ""));
    }
}

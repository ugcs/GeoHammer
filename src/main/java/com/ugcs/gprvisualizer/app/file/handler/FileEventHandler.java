package com.ugcs.gprvisualizer.app.file.handler;

import java.io.File;

public interface FileEventHandler {
    boolean canHandle(File file);
    void handleFileOpened(File file);
    void handleFileOpenError(File file, String errorMessage);
}

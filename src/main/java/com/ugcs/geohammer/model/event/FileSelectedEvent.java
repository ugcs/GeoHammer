package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.format.SgyFile;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class FileSelectedEvent extends BaseEvent {
    @Nullable
    private final SgyFile file;

    public FileSelectedEvent(Object source, @Nullable SgyFile file) {
        super(source);
        this.file = file;
    }

    public FileSelectedEvent(Object source, List<SgyFile> files) {
        super(source);
        this.file = files != null && files.size() > 0 ? files.get(0) : null;
    }

    @Nullable
    public SgyFile getFile() {
        return file;
    }
}

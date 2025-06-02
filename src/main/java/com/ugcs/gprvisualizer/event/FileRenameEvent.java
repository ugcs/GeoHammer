package com.ugcs.gprvisualizer.event;

import com.github.thecoldwine.sigrun.common.ext.SgyFile;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Event triggered when a file is renamed.
 * <p>
 * This event is used to notify components that a file has been renamed,
 * allowing them to update their state accordingly. For example, the GridLayer
 * uses this event to invalidate cached gridding results for the renamed file.
 */
public class FileRenameEvent extends BaseEvent {
    private final SgyFile sgyFile;
    private final File oldFile;

    /**
     * Creates a new FileRenameEvent.
     *
     * @param source  the source of the event
     * @param sgyFile the SgyFile that was renamed
     * @param oldFile the old file path
     */
    public FileRenameEvent(Object source, SgyFile sgyFile, File oldFile) {
        super(source);
        this.sgyFile = sgyFile;
        this.oldFile = oldFile;
    }

    /**
     * Gets the SgyFile that was renamed.
     *
     * @return the SgyFile
     */
    public SgyFile getSgyFile() {
        return sgyFile;
    }

    /**
     * Gets the old file path.
     *
     * @return the old file path
     */
    public File getOldFile() {
        return oldFile;
    }

}
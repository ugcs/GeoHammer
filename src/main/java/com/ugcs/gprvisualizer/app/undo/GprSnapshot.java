package com.ugcs.gprvisualizer.app.undo;

import com.github.thecoldwine.sigrun.common.ext.MetaFile;
import com.github.thecoldwine.sigrun.common.ext.TraceFile;
import com.ugcs.gprvisualizer.app.meta.TraceMeta;
import com.ugcs.gprvisualizer.gpr.Model;

import java.util.Objects;

public class GprSnapshot extends FileSnapshot<TraceFile> {

    private final TraceMeta meta;

    public GprSnapshot(TraceFile file) {
        super(file);

        this.meta = copyMeta(file);
    }

    private static TraceMeta copyMeta(TraceFile file) {
        MetaFile metaFile = file.getMetaFile();
        return metaFile != null
                ? metaFile.getMetaFromState()
                : null;
    }

    @Override
    public void restoreFile(Model model) {
        if (meta == null) {
            return; // no meta
        }
        MetaFile metaFile = file.getMetaFile();
        if (metaFile == null) {
            return; // no meta file
        }

        metaFile.setMetaToState(meta);
        file.updateTracesFromMeta();
    }
}

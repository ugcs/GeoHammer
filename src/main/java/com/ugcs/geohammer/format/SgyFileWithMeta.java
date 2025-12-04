package com.ugcs.geohammer.format;

import com.ugcs.geohammer.format.meta.MetaFile;
import com.ugcs.geohammer.format.meta.TraceMeta;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public abstract class SgyFileWithMeta extends SgyFile {

    protected @Nullable MetaFile metaFile;

    public @Nullable MetaFile getMetaFile() {
        return metaFile;
    }

    @Override
    public List<GeoData> getGeoData() {
        if (metaFile == null) {
            return List.of();
        }
        List<? extends GeoData> values = metaFile.getValues();
        return (List<GeoData>)values;
    }

    abstract public void saveMeta() throws IOException;

    abstract public void syncMeta();

    @Override
    public FileSnapshot<SgyFileWithMeta> createSnapshot() {
        return new TraceFile.Snapshot<>(this);
    }

    public static class Snapshot<T extends SgyFileWithMeta> extends FileSnapshot<T> {

        private final TraceMeta meta;

        public Snapshot(T file) {
            super(file);

            this.meta = copyMeta(file);
        }

        private static TraceMeta copyMeta(SgyFileWithMeta file) {
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
            file.syncMeta();
        }
    }
}

package com.ugcs.geohammer.format;

import com.ugcs.geohammer.format.meta.Meta;
import com.ugcs.geohammer.format.meta.MetaDocument;
import com.ugcs.geohammer.format.meta.MetaFiles;
import com.ugcs.geohammer.format.meta.MetaSchema;
import com.ugcs.geohammer.model.ColumnSchema;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.FileSnapshot;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class SgyFileWithMeta extends SgyFile {

    protected @Nullable Meta meta;

    public @Nullable Meta getMeta() {
        return meta;
    }

    @Override
    public List<GeoData> getGeoData() {
        if (meta == null) {
            return List.of();
        }
        List<? extends GeoData> values = meta.getValues();
        return (List<GeoData>)values;
    }

    protected ColumnSchema createMetaSchema() {
        return MetaSchema.createSchema();
    }

    abstract protected Meta initMeta(ColumnSchema schema);

    protected void loadMeta() throws IOException {
        File source = getFile();
        Check.notNull(source);

        ColumnSchema schema = createMetaSchema();
        Meta newMeta = MetaFiles.readMetaOf(source, schema);
        if (newMeta == null) {
            newMeta = initMeta(schema);
        }

        meta = newMeta;
        syncMeta();
    }

    protected void loadFrom(SgyFileWithMeta other, Runnable load) {
        Check.notNull(load);

        load.run();
        setUnsaved(true);

        ColumnSchema schema = meta != null
                ? meta.getSchema()
                : createMetaSchema();
        meta = other.meta != null
                ? Meta.copy(other.meta, schema)
                : initMeta(schema);
        syncMeta();
    }

    abstract public void syncMeta();

    abstract public void saveMeta() throws IOException;

    @Override
    public FileSnapshot<SgyFileWithMeta> createSnapshot() {
        return new Snapshot<>(this);
    }

    public static class Snapshot<T extends SgyFileWithMeta> extends FileSnapshot<T> {

        private final MetaDocument metaDocument;

        private final ColumnSchema metaSchema;

        public Snapshot(T file) {
            super(file);

            metaDocument = copyMetaDocument(file);
            metaSchema = copyMetaSchema(file);
        }

        private static MetaDocument copyMetaDocument(SgyFileWithMeta file) {
            return file != null && file.getMeta() != null
                    ? MetaDocument.of(file.getMeta())
                    : null;
        }

        private static ColumnSchema copyMetaSchema(SgyFileWithMeta file) {
            return file != null && file.getMeta() != null
                    ? ColumnSchema.copy(file.getMeta().getSchema())
                    : null;
        }

        @Override
        public void restoreFile(Model model) throws IOException {
            if (metaDocument == null || metaSchema == null) {
                return; // no meta
            }

            file.meta = metaDocument.toMeta(metaSchema);
            file.syncMeta();
        }
    }
}

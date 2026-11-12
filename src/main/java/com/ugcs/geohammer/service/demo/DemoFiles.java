package com.ugcs.geohammer.service.demo;

import com.ugcs.geohammer.format.meta.MetaFiles;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Resources;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Component
public class DemoFiles {

    private static final String SOURCES_FOLDER = "data";

    private static final String COPIES_FOLDER = "demo";

    private final Path sourcesPath;

    private final Path copiesPath;

    @Autowired
    public DemoFiles() {
        this(Resources.resolvePath(SOURCES_FOLDER),
                Path.of(System.getProperty("user.home"), ".geohammer", COPIES_FOLDER));
    }

    public DemoFiles(Path sourcesPath, Path copiesPath) {
        Check.notNull(sourcesPath);
        Check.notNull(copiesPath);

        this.sourcesPath = sourcesPath;
        this.copiesPath = copiesPath;
    }

    public File createCopy(DemoFile demoFile) throws IOException {
        Check.notNull(demoFile);

        Path source = sourcesPath.resolve(demoFile.getFileName());
        if (!Files.isRegularFile(source)) {
            throw new NoSuchFileException(source.toString());
        }
        // no file is open while a demo starts, so all previous copies and their sidecars can go
        FileSystemUtils.deleteRecursively(copiesPath);
        Files.createDirectories(copiesPath);
        Path copy = copiesPath.resolve(demoFile.getFileName());
        Files.copy(source, copy);
        Path sourceMeta = MetaFiles.getMetaPath(source.toFile());
        if (Files.isRegularFile(sourceMeta)) {
            Files.copy(sourceMeta, MetaFiles.getMetaPath(copy.toFile()));
        }
        return copy.toFile();
    }

    public boolean isInDemoFolder(File file) {
        Check.notNull(file);

        Path folder = file.toPath().toAbsolutePath().normalize().getParent();
        return copiesPath.toAbsolutePath().normalize().equals(folder);
    }
}

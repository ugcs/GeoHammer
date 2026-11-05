package com.ugcs.geohammer.service.demo;

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

    private static final String DEMO_FOLDER = "demo";

    private final Path sourcesPath;

    private final Path copiesPath;

    @Autowired
    public DemoFiles() {
        this(Resources.resolvePath(DEMO_FOLDER),
                Path.of(System.getProperty("user.home"), ".geohammer", DEMO_FOLDER));
    }

    public DemoFiles(Path sourcesPath, Path copiesPath) {
        Check.notNull(sourcesPath);
        Check.notNull(copiesPath);

        this.sourcesPath = sourcesPath;
        this.copiesPath = copiesPath;
    }

    public File createCopy(DemoFile demoFile) throws IOException {
        Check.notNull(demoFile);

        Path source = sourcesPath.resolve(demoFile.getFolder()).resolve(demoFile.getFileName());
        if (!Files.isRegularFile(source)) {
            throw new NoSuchFileException(source.toString());
        }
        Path copyFolder = copiesPath.resolve(demoFile.getFolder());
        FileSystemUtils.deleteRecursively(copyFolder);
        Files.createDirectories(copyFolder);
        Path copy = copyFolder.resolve(demoFile.getFileName());
        Files.copy(source, copy);
        return copy.toFile();
    }
}

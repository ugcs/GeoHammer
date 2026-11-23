package com.ugcs.geohammer.service.demo;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Resources;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public enum DemoSurvey {

    GPR("GPR Grid Survey Demo", "demo-gpr.sgy"),
    CSV("MagNIMBUS Survey Demo", "demo-csv.csv");

    private static final Path FOLDER = Resources.resolvePath("data").toAbsolutePath().normalize();

    private final String title;

    private final String fileName;

    DemoSurvey(String title, String fileName) {
        this.title = title;
        this.fileName = fileName;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public File getFile() throws NoSuchFileException {
        Path path = FOLDER.resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new NoSuchFileException(path.toString());
        }
        return path.toFile();
    }

    public static boolean isDemoFile(File file) {
        Check.notNull(file);

        Path path = file.toPath().toAbsolutePath().normalize();
        for (DemoSurvey demoSurvey : values()) {
            if (FOLDER.resolve(demoSurvey.fileName).equals(path)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDemoFile(SgyFile sgyFile) {
        Check.notNull(sgyFile);

        File file = sgyFile.getFile();
        return file != null && isDemoFile(file);
    }
}

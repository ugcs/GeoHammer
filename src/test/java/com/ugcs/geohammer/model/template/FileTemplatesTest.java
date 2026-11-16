package com.ugcs.geohammer.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ugcs.geohammer.model.event.UserFolderChangedEvent;
import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.UserFolder;
import com.ugcs.geohammer.view.status.Status;

class FileTemplatesTest {

    private static final Status STATUS = new Status() {
        @Override
        public void showProgressText(String txt) {
        }

        @Override
        public void showMessage(String message, String source) {
        }
    };

    @TempDir
    Path root;

    private UserFolder userFolder;

    private Path userPath;

    private Path appPath;

    @BeforeEach
    void setUp() throws Exception {
        // settings values are kept in memory: they are written to disk only on save
        userFolder = new UserFolder(new Settings(), event -> { }, root.resolve("user"));
        userPath = userFolder.getTemplatesPath();
        appPath = Files.createDirectories(root.resolve("app").resolve("templates"));
    }

    @Test
    void createsMissingUserFolder() throws Exception {
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");

        FileTemplates templates = loadTemplates();

        assertTrue(Files.isDirectory(userPath));
        assertEquals(1, templates.getTemplates().size());
    }

    @Test
    void loadsTemplatesFromBothFolders() throws Exception {
        writeTemplate(userPath, "user.yaml", "Sonar", "Depth", "m");
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");

        FileTemplates templates = loadTemplates();

        assertEquals(2, templates.getTemplates().size());
        assertNotNull(templates.getTemplate("Sonar"));
        assertNotNull(templates.getTemplate("Mag"));
    }

    @Test
    void listsUserTemplatesBeforeAppTemplates() throws Exception {
        writeTemplate(userPath, "user.yaml", "UserMag", "Mag", "nT");
        writeTemplate(appPath, "app.yaml", "AppMag", "Mag", "nT");

        FileTemplates templates = loadTemplates();

        assertEquals(2, templates.getTemplates().size());
        assertEquals("UserMag", templates.getTemplates().get(0).getName());
        assertEquals("AppMag", templates.getTemplates().get(1).getName());
    }

    @Test
    void userTemplateReplacesAppTemplateOfSameName() throws Exception {
        // user template of the same name matches another header
        writeTemplate(userPath, "user.yaml", "Mag", "Depth", "m");
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");

        FileTemplates templates = loadTemplates();

        assertEquals(1, templates.getTemplates().size());
        assertEquals("m", getUnits(templates.getTemplate("Mag")));
    }

    @Test
    void keepsAppTemplatesWhenUserFolderIsEmpty() throws Exception {
        Files.createDirectories(userPath);
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");

        FileTemplates templates = loadTemplates();

        assertNotNull(templates.getTemplate("Mag"));
    }

    @Test
    void loadsUserTemplatesWhenAppFolderIsMissing() throws Exception {
        writeTemplate(userPath, "user.yaml", "Mag", "Mag", "nT");
        appPath = root.resolve("missing");

        FileTemplates templates = loadTemplates();

        assertEquals(1, templates.getTemplates().size());
    }

    @Test
    void restoresAppTemplateAfterUserTemplateIsDeleted() throws Exception {
        Path userTemplate = writeTemplate(userPath, "user.yaml", "Mag", "Depth", "m");
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");
        assertEquals("m", getUnits(loadTemplates().getTemplate("Mag")));

        Files.delete(userTemplate);
        FileTemplates templates = loadTemplates();

        assertEquals("nT", getUnits(templates.getTemplate("Mag")));
    }

    @Test
    void loadsTemplatesFromChangedUserFolder() throws Exception {
        writeTemplate(userPath, "user.yaml", "Sonar", "Depth", "m");
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");
        Path otherUserFolder = root.resolve("other");
        writeTemplate(otherUserFolder.resolve("templates"), "other.yaml", "Mag", "Mag", "uT");

        userFolder.setPath(otherUserFolder);
        FileTemplates templates = loadTemplates();

        assertEquals(1, templates.getTemplates().size());
        assertEquals("uT", getUnits(templates.getTemplate("Mag")));
    }

    @Test
    void reloadsTemplatesWhenUserFolderChanges() throws Exception {
        writeTemplate(userPath, "user.yaml", "Sonar", "Depth", "m");
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");
        FileTemplates templates = loadTemplates();
        Path otherUserFolder = root.resolve("other");

        userFolder.setPath(otherUserFolder);
        templates.onUserFolderChanged(new UserFolderChangedEvent(userFolder));

        assertTrue(Files.isDirectory(otherUserFolder.resolve("templates")));
        assertEquals(1, templates.getTemplates().size());
        assertEquals("nT", getUnits(templates.getTemplate("Mag")));
    }

    private FileTemplates loadTemplates() throws Exception {
        FileTemplates templates = new FileTemplates(STATUS, userFolder, appPath);
        templates.afterPropertiesSet();
        return templates;
    }

    private static String getUnits(Template template) {
        return template.getDataMapping().getDataValues().getFirst().getUnits();
    }

    // template matching files with the value column in the header
    private static Path writeTemplate(Path directory, String fileName, String name, String valueHeader,
            String units) throws Exception {
        String yaml = """
                name: "%s"
                file-type: CSV
                match-regex: >-
                  ^.*Date,Time,Latitude,Longitude,%s.*
                file-format:
                  has-header: true
                  comment-prefix: '%%'
                  decimal-separator: '.'
                  separator: ','
                data-mapping:
                  latitude:
                    header: 'Latitude'
                  longitude:
                    header: 'Longitude'
                  time:
                    header: 'Time'
                    format: 'H:mm:ss.fff'
                  date:
                    header: 'Date'
                    format: 'yyyy/MM/dd'
                  data-values:
                    - header: "%s"
                      semantic: "%s"
                      units: "%s"
                """.formatted(name, valueHeader, valueHeader, valueHeader, units);
        Files.createDirectories(directory);
        return Files.writeString(directory.resolve(fileName), yaml);
    }
}

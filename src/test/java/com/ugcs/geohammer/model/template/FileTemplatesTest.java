package com.ugcs.geohammer.model.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private Path userPath;

    private Path appPath;

    @BeforeEach
    void setUp() throws Exception {
        userPath = root.resolve("user").resolve("templates");
        appPath = Files.createDirectories(root.resolve("app").resolve("templates"));
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
    void keepsAppTemplatesWhenUserTemplatesFolderIsEmpty() throws Exception {
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
    void reloadsTemplatesFromChangedUserTemplatesFolder() throws Exception {
        writeTemplate(userPath, "user.yaml", "Sonar", "Depth", "m");
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");
        Path otherUserPath = root.resolve("other");
        writeTemplate(otherUserPath, "other.yaml", "Mag", "Mag", "uT");
        FileTemplates templates = loadTemplates();

        templates.watch(otherUserPath);

        assertEquals(1, templates.getTemplates().size());
        assertEquals("uT", getUnits(templates.getTemplate("Mag")));
    }

    @Test
    void createsChangedUserTemplatesFolder() throws Exception {
        writeTemplate(appPath, "app.yaml", "Mag", "Mag", "nT");
        FileTemplates templates = loadTemplates();
        Path otherUserPath = root.resolve("other").resolve("templates");

        templates.watch(otherUserPath);

        assertTrue(Files.isDirectory(otherUserPath));
        assertEquals(1, templates.getTemplates().size());
        assertEquals("nT", getUnits(templates.getTemplate("Mag")));
    }

    @Test
    void ignoresSameUserTemplatesFolder() throws Exception {
        FileTemplates templates = loadTemplates();
        writeTemplate(userPath, "user.yaml", "Sonar", "Depth", "m");

        templates.watch(userPath);

        // a change would reload the list and pick up the new template
        assertEquals(0, templates.getTemplates().size());
    }

    private FileTemplates loadTemplates() throws Exception {
        FileTemplates templates = new FileTemplates(STATUS, userPath, appPath);
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

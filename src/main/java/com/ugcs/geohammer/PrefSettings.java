package com.ugcs.geohammer;

import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Service
public class PrefSettings {

    @Value("${settings.prefix:geohammer.settings.}")
    private String prefix = "geohammer.settings.";

    private final Path path;

    private final Properties properties;

    public PrefSettings(FileTemplates templates) {
        path = templates.getTemplatesPath().resolve("templates-settings.properties");
        properties = loadProperties(path);
    }

    private Properties loadProperties(Path path) {
        Check.notNull(path);

        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                properties.load(r);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return properties;
    }

    private void saveProperties(Properties properties, Path path) {
        Check.notNull(properties);
        Check.notNull(path);

        try (Writer w = Files.newBufferedWriter(path)) {
            properties.store(w, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getPropertyKey(String group, String name) {
        Check.notEmpty(name);

        String key = prefix;
        if (!Strings.isNullOrEmpty(group)) {
            key += group + ".";
        }
        key += name;
        return key;
    }

    public String getSetting(String group, String name) {
        String propertyKey = getPropertyKey(group, name);
        return properties.getProperty(propertyKey);
    }

    private void setSetting(String group, String name, @Nullable Object value) {
        String propertyKey = getPropertyKey(group, name);
        String propertyValue = value != null
                ? value.toString()
                : null;

        if (Strings.isNullOrEmpty(propertyValue)) {
            properties.remove(propertyKey);
        } else {
            properties.setProperty(propertyKey, propertyValue);
        }
    }

    public void saveSetting(String group, String name, @Nullable Object value) {
        setSetting(group, name, value);
        saveProperties(properties, path);
    }
}
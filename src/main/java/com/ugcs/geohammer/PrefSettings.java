package com.ugcs.geohammer;

import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Strings;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Service
public class PrefSettings {

    private static final Logger log = LoggerFactory.getLogger(PrefSettings.class);

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
                log.info("Properties loaded from {}", path);
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
            log.info("Properties saved to {}", path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        saveProperties(properties, path);
    }

    @PreDestroy
    public void onShutdown() {
        save();
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

    public @Nullable String getString(String group, String name) {
        String propertyKey = getPropertyKey(group, name);
        return properties.getProperty(propertyKey);
    }

    public String getStringOrDefault(String group, String name, String defaultValue) {
        String value = getString(group, name);
        return !Strings.isNullOrEmpty(value) ? value : defaultValue;
    }

    public @Nullable Integer getInt(String group, String name) {
        String value = getString(group, name);
        return !Strings.isNullOrEmpty(value) ? Integer.parseInt(value) : null;
    }

    public int getIntOrDefault(String group, String name, int defaultValue) {
        Integer value = getInt(group, name);
        return value != null ? value : defaultValue;
    }

    public @Nullable Double getDouble(String group, String name) {
        String value = getString(group, name);
        return !Strings.isNullOrEmpty(value) ? Double.parseDouble(value) : null;
    }

    public double getDoubleOrDefault(String group, String name, double defaultValue) {
        Double value = getDouble(group, name);
        return value != null ? value : defaultValue;
    }

    public @Nullable Boolean getBoolean(String group, String name) {
        String value = getString(group, name);
        return !Strings.isNullOrEmpty(value) ? Boolean.parseBoolean(value) : null;
    }

    public boolean getBooleanOrDefault(String group, String name, boolean defaultValue) {
        Boolean value = getBoolean(group, name);
        return value != null ? value : defaultValue;
    }

    public void setValue(String group, String name, @Nullable Object value) {
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
}
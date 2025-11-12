package com.ugcs.geohammer.analytics.amplitude;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Service
public class UserPropertiesService {

    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".geohammer", "user.properties");

    private final Properties properties = new Properties();

    public UserPropertiesService() {
        load();
    }

    private void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                properties.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Could not load user properties", e);
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                properties.store(out, "User Properties");
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not save user properties", e);
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public void put(String key, String value) {
        properties.setProperty(key, value);
    }

    public boolean contains(String key) {
        return properties.containsKey(key);
    }

    public void remove(String key) {
        properties.remove(key);
    }
}

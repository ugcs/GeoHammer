package com.ugcs.gprvisualizer.gpr;

import com.ugcs.gprvisualizer.app.yaml.FileTemplates;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class PrefSettings {

    @Value("${settings.prefix:geohammer.settings.}")
    private String prefix = "geohammer.settings.";

    //private final Resource resource = new ClassPathResource("dynamic-settings.properties");

    private final Resource resource;

    public PrefSettings(FileTemplates templates) {
        resource = new FileSystemResourceLoader().getResource("file:" + templates.getTemplatesPath().toString() + "/templates-settings.properties");
    }

    public Map<String, Map<String, String>> getAllSettings() {

        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Map<String, Map<String, String>> groupedSettings = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String[] parts = key.substring(prefix.length()).split("\\.", 2);
                if (parts.length == 2) {
                    String group = parts[0];
                    String setting = parts[1];
                    groupedSettings
                        .computeIfAbsent(group, k -> new HashMap<>())
                        .put(setting, properties.getProperty(key));
                }
            }
        }
        return groupedSettings;
    }

    public String getSetting(String group, String name) {
        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties.getProperty(prefix + group + "." + name);
    }

    public void saveSetting(String group, Map<String, ?> values) {
        Properties properties = new Properties();
        try (InputStream input = resource.getInputStream()) {
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try (OutputStream output = new FileOutputStream(resource.getFile())) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                properties.setProperty(prefix + group + "." + entry.getKey(), entry.getValue().toString());
            }
            properties.store(output, null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
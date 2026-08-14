package com.ugcs.geohammer.model.template;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Resources;
import com.ugcs.geohammer.util.TextFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import com.ugcs.geohammer.view.status.Status;
import com.ugcs.geohammer.model.template.Template.FileType;

@Component
public class FileTemplates implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(FileTemplates.class);

    public static final String TEMPLATES_FOLDER = "templates";

    private static final int MAX_LINE_LENGTH = 4096;

    private final List<Template> templates = new CopyOnWriteArrayList<>();

    private Yaml yaml;

    private Path templatesPath;

    private final Status status;

    @Value( "${app.filetemplates.linethreshold:50}")
    private int lineThreshold;

    public FileTemplates(Status status) {
        this.status = status;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("Loading templates...");

        yaml = createYaml();

        this.templatesPath = Resources.resolvePath(TEMPLATES_FOLDER);
        templates.addAll(loadTemplates());

        if (!templates.isEmpty()) {
            status.showMessage("Loaded " + templates.size() + " templates", "Templates");
        }
    }

    // yaml instances are not thread-safe: a new one is created
    // for every load outside of the template watcher
    private static Yaml createYaml() {
        Constructor c = new Constructor(Template.class, new LoaderOptions());

        c.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                if (name.indexOf('-') > -1) {
                    name = toCamelCase(name);
                }
                return super.getProperty(type, name);
            }

            private String toCamelCase(String name) {
                String[] parts = name.split("-");
                StringBuilder sb = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    sb.append(StringUtils.capitalize(parts[i]));
                }
                return sb.toString();
            }
        });

        c.getPropertyUtils().setSkipMissingProperties(true);
        return new Yaml(c);
    }

    private Template loadTemplate(Yaml yaml, Reader r) {
        return loadTemplate(yaml, r, false);
    }

    private Template loadTemplate(Yaml yaml, Reader r, boolean silent) {
        Check.notNull(yaml);
        if (r == null) {
            return null;
        }
        try {
            Template template = yaml.load(new BufferedReader(r));
            if (template == null) {
                return null;
            }
            template.init();
            if (template.isTemplateValid()) {
                if (!silent) {
                    log.debug("Valid template: {}", template);
                }
                return template;
            } else {
                if (!silent) {
                    log.error("Invalid template: {}", template);
                    status.showMessage("Invalid template: " + template, "Templates");
                }
            }
        } catch (RuntimeException e) {
            if (!silent) {
                log.error("Error reading template: {}", e.getMessage());
            }
        }
        return null;
    }

    private List<Template> loadTemplates(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return List.of();
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            return List.of();
        }
        Arrays.sort(files);

        Yaml yaml = createYaml();
        List<Template> templates = new ArrayList<>();
        for (File file : files) {
            try (Reader r = new FileReader(file)) {
                Template template = loadTemplate(yaml, r, true);
                if (template != null) {
                    templates.add(template);
                }
            } catch (IOException e) {
                log.warn("Error reading template", e);
            }
        }
        return templates;
    }

    private List<Template> loadTemplates() {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver()
                    .getResources("file:" + templatesPath.toString() + "/*.yaml");
        } catch (IOException e) {
            log.error("Error reading templates folder", e);
            return List.of();
        }

        List<Template> templates = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            try (Reader r = new InputStreamReader(resource.getInputStream())) {
                Template template = loadTemplate(yaml, r);
                if (template != null) {
                    templates.add(template);
                }
            } catch (IOException e) {
                log.error("Error reading template", e);
                // try next
            }
        }
        if (templates.isEmpty()) {
            log.error("No templates found in {}", templatesPath);
        }
        return templates;
    }

    @Async
    public void watchTemplates() {
        if (templatesPath == null) {
            return;
        }

        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            templatesPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (event.context() instanceof Path templatePath && templatePath.toString().endsWith(".yaml")) {
                            String templateName = templatePath.toString();
                            log.info("Template file created: {}", templateName);
                            status.showMessage("Template created: " + templateName, "Templates");
                        }
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        if (event.context() instanceof Path templatePath && templatePath.toString().endsWith(".yaml")) {
                            String templateName = templatePath.toString();
                            log.info("Template file deleted: {}", templateName);
                            status.showMessage("Template deleted: " + templateName, "Templates");
                        }
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if (event.context() instanceof Path templatePath && templatePath.toString().endsWith(".yaml")) {
                            String templateName = templatePath.toString();
                            log.info("Template file modified: {}", templateName);
                            status.showMessage("Template updated: " + templateName, "Templates");
                        } else {
                            continue;
                        }
                    }
                    // load before replacing to keep templates readable while reloading
                    List<Template> reloaded = loadTemplates();
                    templates.clear();
                    templates.addAll(reloaded);

                    if (!templates.isEmpty()) {
                        status.showMessage("Reloaded " + templates.size() + " templates", "Templates");
                    }
                }
                // To receive further events, reset the key
                key.reset();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error reading template: {}", e.getMessage());
        }
    }

    public List<Template> getTemplates() {
        return templates;
    }

    public Template getTemplate(String templateName) {
        for (Template template : templates) {
            if (Objects.equals(template.getName(), templateName)) {
                return template;
            }
        }
        return null;
    }

    public Template findTemplate(File file) {
        if (file.getName().endsWith(".sgy")) {
            var ot = templates.stream()
                    .filter(t -> FileType.Segy.equals(t.getFileType()))
                    .findFirst();
            return ot.orElse(null);
        }

        String firstLines = readFirstLines(file);

        // templates in the file directory take precedence
        // over the application templates
        File directory = file.getAbsoluteFile().getParentFile();
        Template local = matchTemplate(loadTemplates(directory), firstLines);
        if (local != null) {
            return local;
        }
        return matchTemplate(templates, firstLines);
    }

    // bounded read: an over-long line stops reading, so binary
    // content cannot produce huge inputs for the template regexes
    private String readFirstLines(File file) {
        try {
            List<String> firstLines = TextFiles.readLines(file, lineThreshold, MAX_LINE_LENGTH);
            return String.join(System.lineSeparator(), firstLines);
        } catch (IOException e) {
            log.error("Error reading file: {}", e.getMessage());
            return "";
        }
    }

    private static Template matchTemplate(List<Template> templates, String content) {
        for (var t : templates) {
            try {
                var regex = Pattern.compile(t.getMatchRegex(), Pattern.MULTILINE | Pattern.DOTALL);
                if (regex.matcher(content).find()) {
                    return t;
                }
            } catch (Exception e) {
                log.error("Error matching template: " + e.getMessage());
            }
        }
        return null;
    }

    public Path getTemplatesPath() {
        return templatesPath;
    }
}

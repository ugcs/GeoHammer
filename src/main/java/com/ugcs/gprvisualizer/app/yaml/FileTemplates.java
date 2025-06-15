package com.ugcs.gprvisualizer.app.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import com.ugcs.gprvisualizer.app.MessageBoxHelper;
import com.ugcs.gprvisualizer.app.yaml.Template.FileType;

@Component
public class FileTemplates implements InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(FileTemplates.class);

    private static final String TEMPLATES_FOLDER = "templates";

    private final List<Template> templates = new ArrayList<>();

    private Yaml yaml;

    private Path templatesPath;

    @Value( "${app.filetemplates.linethreshold:50}")
    private int lineThreshold;

    @Override
    public void afterPropertiesSet() throws Exception {

        logger.info("Loading templates...");

        Constructor c = new Constructor(Template.class, new LoaderOptions());

        c.setPropertyUtils(new PropertyUtils() {
            @Override
            public Property getProperty(Class<? extends Object> type, String name) {
                if (name.indexOf('-') > -1) {
                    name = toCameCase(name);
                }
                return super.getProperty(type, name);
            }

            private String toCameCase(String name) {
                String[] parts = name.split("-");
                StringBuilder sb = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    sb.append(StringUtils.capitalize(parts[i]));
                }
                return sb.toString();
            }
        });

        c.getPropertyUtils().setSkipMissingProperties(true);
        yaml = new Yaml(c);

        

        Path templatesPath = Path.of(TEMPLATES_FOLDER);
        if (!Files.exists(templatesPath)) {
            Path currentDir = Paths.get(FileTemplates.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            System.out.println("Current Directory: " + currentDir);
            
            templatesPath = currentDir.resolve(TEMPLATES_FOLDER);
            System.out.println("Data file path: " + templatesPath);
        }

        this.templatesPath = loadTemplates(yaml, templatesPath, templates);
    }

    private Path loadTemplates(Yaml yaml, Path templatesPath, List<Template> templates) {
        try {
            // Get all resources ending with .yaml from path
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("file:" + templatesPath.toString() + "/*.yaml");

            for (Resource resource : resources) {
                templatesPath = templatesPath == null ? resource.getFile().getParentFile().toPath() : templatesPath;
                try (InputStream inputStream = resource.getInputStream()) {
                    try {
                        Template template = yaml.load(inputStream);
                        if (template.isTemplateValid()) {
                            templates.add(template);
                            logger.debug("Valid template, data: " + template);
                        } else {
                            logger.error("Invalid template: " + template);
                        }
                    } catch (YAMLException e) {
                        logger.error("Error reading template: " + e.getMessage());
                    }
                }
            }
            if (templates.isEmpty()) {
                logger.error("No templates found in " + templatesPath);
            }
            return templatesPath;
        } catch (IOException e) {
            logger.error("Error reading template: " + e.getMessage());
        }
        return null;
    }

    @Async
    public void watchTemplates() {
        if (templatesPath == null) {
            return;
        }

        try {
            // Create a WatchService
            WatchService watchService = FileSystems.getDefault().newWatchService();

            // Register the directory for specific events
            templatesPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);

            //System.out.println("Watching directory: " + directoryPath);

            // Infinite loop to continuously watch for events
            while (true) {
                //System.out.println("watch started");
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    // Handle the specific event
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        System.out.println("File created: " + event.context());
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        System.out.println("File deleted: " + event.context());
                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        if (event.context() instanceof Path templatePath && templatePath.toString().endsWith(".yaml")) {
                            logger.info("Template file modified: " + templatePath);
                        } else {
                            continue;
                        }
                    }
                    templates.clear();
                    loadTemplates(yaml, templatesPath, templates);
                }

                // To receive further events, reset the key
                key.reset();
            }

        } catch (IOException | InterruptedException e) {
            logger.error("Error reading template: " + e.getMessage());
        }
    }

    public List<Template> getTemplates() {
        return templates;
    }

    public Template findTemplate(List<Template> templates, String fileName) {
        if (fileName.endsWith(".sgy")) {
            var ot = templates.stream()
                    .filter(t -> FileType.Segy.equals(t.getFileType()))
                    .findFirst();
            return ot.isPresent() ? ot.get() : null;
        }

        String firstNonEmptyLines = "";
        try (Stream<String> lines = Files.lines(Paths.get(fileName))) {
            List<String> firstTenLines = lines.limit(lineThreshold).collect(Collectors.toList());
            firstNonEmptyLines = String.join(System.lineSeparator(), firstTenLines);
            logger.debug(firstNonEmptyLines);
        } catch (IOException e) {
            logger.error("Error reading file: " + e.getMessage());
        }

        for (var t : templates) {
            try {
                var regex = Pattern.compile(t.getMatchRegex(), Pattern.MULTILINE | Pattern.DOTALL);
                if (regex.matcher(firstNonEmptyLines).find()) {
                    return t;
                }
            } catch (Exception e) {
                logger.error("Error matching template: " + e.getMessage());
            }
        }

        return null;
    }

    private Template createSegyTemplate() {
        return new Template() {
            @Override
            public String getCode() {
                return "Segy";
            }

            @Override
            public FileType getFileType() {
                return FileType.Segy;
            }

            @Override
            public String getName() {
                return "Segy";
            }
        };
    }

    public Path getTemplatesPath() {
        return templatesPath;
    }
}

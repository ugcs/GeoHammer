package com.ugcs.geohammer.model.template;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.model.ConfigurableFolder;
import com.ugcs.geohammer.util.UserDataFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class UserTemplatesFolder extends ConfigurableFolder {

    private static final String DEFAULT_TEMPLATES_FOLDER = "templates";

    private static final String PREF_USER_TEMPLATES = "userTemplates";

    public UserTemplatesFolder(Settings settings) {
        super(settings, PREF_USER_TEMPLATES, getDefaultPath());
    }

    public static Path getDefaultPath() {
        return UserDataFolder.getPath().resolve(DEFAULT_TEMPLATES_FOLDER);
    }

    boolean setPath(@Nullable Path path) {
        return updatePath(path);
    }
}

package com.ugcs.geohammer.service.script;

import com.ugcs.geohammer.Settings;
import com.ugcs.geohammer.model.ConfigurableFolder;
import com.ugcs.geohammer.util.UserDataFolder;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class UserScriptsFolder extends ConfigurableFolder {

	private static final String DEFAULT_SCRIPTS_FOLDER = "scripts";

	private static final String PREF_USER_SCRIPTS = "userScripts";

	public UserScriptsFolder(Settings settings) {
		super(settings, PREF_USER_SCRIPTS, getDefaultPath());
	}

	public static Path getDefaultPath() {
		return UserDataFolder.getPath().resolve(DEFAULT_SCRIPTS_FOLDER);
	}

	boolean setPath(@Nullable Path path) {
		return updatePath(path);
	}
}

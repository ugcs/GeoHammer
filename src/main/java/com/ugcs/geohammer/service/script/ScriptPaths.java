package com.ugcs.geohammer.service.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Resources;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScriptPaths {

	private static final String SCRIPTS_DIRECTORY = "scripts";
	private static final String CHECK_IMPORTS_SCRIPT = "check_imports.py";

	private final UserScriptsFolder userScriptsFolder;

	private final Path scriptsPath;

	@Autowired
	public ScriptPaths(UserScriptsFolder userScriptsFolder) {
		// absolute: commands that get script paths may run in another working directory
		this(userScriptsFolder, Resources.resolvePath(SCRIPTS_DIRECTORY).toAbsolutePath());
	}

	public ScriptPaths(UserScriptsFolder userScriptsFolder, Path scriptsPath) {
		this.userScriptsFolder = Check.notNull(userScriptsFolder);
		this.scriptsPath = scriptsPath;
		userScriptsFolder.createFolder();
	}

	public Path getScriptsPath() {
		return scriptsPath;
	}

	public Path getUserScriptsPath() {
		return userScriptsFolder.getPath();
	}

	public void setUserScriptsPath(@Nullable Path path) {
		if (!userScriptsFolder.setPath(path)) {
			return;
		}
		userScriptsFolder.createFolder();
	}

	public Path findScriptFile(String scriptFilename) throws IOException {
		Path userScriptFile = getUserScriptsPath().resolve(scriptFilename);
		if (Files.isRegularFile(userScriptFile)) {
			return userScriptFile;
		}
		Path scriptFile = scriptsPath.resolve(scriptFilename);
		if (Files.isRegularFile(scriptFile)) {
			return scriptFile;
		}
		throw new IOException("Script file not found: " + scriptFilename);
	}

	public Path getCheckImportsScript() {
		return scriptsPath.resolve(CHECK_IMPORTS_SCRIPT);
	}

}

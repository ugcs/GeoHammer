package com.ugcs.geohammer.service.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ugcs.geohammer.model.PathSettings;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScriptPaths {

	private static final Logger log = LoggerFactory.getLogger(ScriptPaths.class);

	private static final String SCRIPTS_DIRECTORY = "scripts";
	private static final String CHECK_IMPORTS_SCRIPT = "check_imports.py";

	private final Path scriptsPath;

	private volatile Path userScriptsPath;

	@Autowired
	public ScriptPaths(PathSettings pathSettings) {
		// absolute: commands that get script paths may run in another working directory
		this(pathSettings.getUserScriptsPath(), Resources.resolvePath(SCRIPTS_DIRECTORY).toAbsolutePath());
	}

	public ScriptPaths(Path userScriptsPath, Path scriptsPath) {
		this.userScriptsPath = Check.notNull(userScriptsPath);
		this.scriptsPath = scriptsPath;
		createUserScriptsFolder();
	}

	public Path getScriptsPath() {
		return scriptsPath;
	}

	public Path getUserScriptsPath() {
		return userScriptsPath;
	}

	public void setUserScriptsPath(Path path) {
		Check.notNull(path);
		if (path.equals(userScriptsPath)) {
			return;
		}
		userScriptsPath = path;
		createUserScriptsFolder();
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

	private void createUserScriptsFolder() {
		Path userScriptsPath = getUserScriptsPath();
		try {
			Files.createDirectories(userScriptsPath);
		} catch (IOException e) {
			log.warn("Cannot create scripts folder {}", userScriptsPath, e);
		}
	}
}

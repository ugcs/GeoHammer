package com.ugcs.geohammer.service.script;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.ugcs.geohammer.model.template.FileTemplates;
import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScriptPaths {

	private static final Logger log = LoggerFactory.getLogger(ScriptPaths.class);

	private static final String SCRIPTS_DIRECTORY = "scripts";
	private static final String CHECK_IMPORTS_SCRIPT = "check_imports.py";

	private final UserScriptsFolder userScriptsFolder;

	private final Path scriptsPath;

	@Autowired
	public ScriptPaths(UserScriptsFolder userScriptsFolder) {
		this(userScriptsFolder, resolveScriptsPath());
	}

	public ScriptPaths(UserScriptsFolder userScriptsFolder, Path scriptsPath) {
		this.userScriptsFolder = Check.notNull(userScriptsFolder);
		this.scriptsPath = scriptsPath;
		createUserScriptsFolder();
	}

	public Path getScriptsPath() {
		return scriptsPath;
	}

	public Path getUserScriptsPath() {
		return userScriptsFolder.getPath();
	}

	public void setUserScriptsPath(@Nullable Path path) {
		Path previousPath = userScriptsFolder.getPath();
		userScriptsFolder.setPath(path);
		if (userScriptsFolder.getPath().equals(previousPath)) {
			return;
		}
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

	private static Path resolveScriptsPath() {
		// Try project root first (for IDE/dev)
		Path projectRoot = Paths.get(System.getProperty("user.dir"));
		Path scriptsInRoot = projectRoot.resolve(SCRIPTS_DIRECTORY);
		if (scriptsInRoot.toFile().exists()) {
			return scriptsInRoot;
		}

		// Fallback to target/scripts (for packaged app)
		URI codeSource;
		try {
			codeSource = FileTemplates.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		Path currentDir = Paths.get(codeSource).getParent();
		return currentDir.resolve(SCRIPTS_DIRECTORY);
	}
}

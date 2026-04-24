package com.ugcs.geohammer.service.script;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.ugcs.geohammer.model.template.FileTemplates;
import org.springframework.stereotype.Component;

@Component
public class ScriptPaths {

	private static final String SCRIPTS_DIRECTORY = "scripts";
	private static final String CHECK_IMPORTS_SCRIPT = "check_imports.py";

	private final Path scriptsPath;

	public ScriptPaths() {
		this.scriptsPath = resolveScriptsPath();
	}

	public Path getScriptsPath() {
		return scriptsPath;
	}

	public Path getCheckImportsScript() {
		return scriptsPath.resolve(CHECK_IMPORTS_SCRIPT);
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

package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScriptDependencies {

	private static final Logger log = LoggerFactory.getLogger(ScriptDependencies.class);

	private final PackageInstaller packageInstaller;

	private final ScriptRequirements scriptRequirements;

	private final ScriptImports scriptImports;

	public ScriptDependencies(PackageInstaller packageInstaller, ScriptRequirements scriptRequirements,
			ScriptImports scriptImports) {
		this.packageInstaller = packageInstaller;
		this.scriptRequirements = scriptRequirements;
		this.scriptImports = scriptImports;
	}

	public void install(File scriptFile, Consumer<String> onOutput)
			throws IOException, InterruptedException, DependencyImportException {
		installDependencies(scriptFile, onOutput, false);
	}

	public void reinstall(File scriptFile, Consumer<String> onOutput)
			throws IOException, InterruptedException, DependencyImportException {
		installDependencies(scriptFile, onOutput, true);
	}

	private void installDependencies(File scriptFile, Consumer<String> onOutput, boolean forceReinstall)
			throws IOException, InterruptedException, DependencyImportException {
		String filename = scriptFile.getName();

		if (!forceReinstall) {
			try {
				scriptImports.verify(scriptFile);
				onOutput.accept("Dependencies already satisfied for script " + filename);
				return;
			} catch (DependencyImportException e) {
				log.debug("Initial import check failed: {}", e.getMessage());
			}
		}

		ScriptRequirements.Requirements requirements = scriptRequirements.derive(scriptFile, onOutput);
		if (requirements == null) {
			return;
		}
		try (requirements) {
			Path requirementsPath = requirements.path();
			if (requirementsPath == null) {
				onOutput.accept("No dependencies found for script " + filename);
			} else if (forceReinstall) {
				packageInstaller.reinstallFromRequirements(requirementsPath, onOutput);
			} else {
				packageInstaller.installFromRequirements(requirementsPath, onOutput);
			}

			scriptImports.verify(scriptFile);
			if (requirementsPath != null) {
				onOutput.accept(forceReinstall
						? "Dependencies reinstalled for script " + filename
						: "Dependencies installed for script " + filename);
			}
		}
	}
}

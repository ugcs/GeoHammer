package com.ugcs.geohammer.service.script.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.CommandExecutionException;
import com.ugcs.geohammer.service.script.CommandExecutor;
import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;
import com.ugcs.geohammer.util.FileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PythonDependenciesInstaller {

	private static final Logger log = LoggerFactory.getLogger(PythonDependenciesInstaller.class);

	private static final String REQUIREMENTS_ANALYZER = "pipreqs";

	private final DependencyResolver dependencyResolver;

	private final PythonPackageManager pythonPackageManager;

	private final Set<String> installedScripts = ConcurrentHashMap.newKeySet();

	public PythonDependenciesInstaller(ExecutorService executorService,
									   PythonExecutorPathResolver pythonExecutorPathResolver,
									   CommandExecutor commandExecutor) {
		this.dependencyResolver = new PipreqsDependencyResolver(
				executorService,
				pythonExecutorPathResolver,
				commandExecutor);
		this.pythonPackageManager = new PythonPackageManager(
				executorService,
				pythonExecutorPathResolver,
				commandExecutor);
	}

	/**
	 * Installs the required Python dependencies for the given script file if they are not already installed.
	 *
	 * @param file     the script file
	 * @param onOutput a consumer to handle output messages
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if the operation is interrupted
	 */
	public void installIfNeeded(File file, Consumer<String> onOutput) throws IOException, InterruptedException {
		String cacheKey = generateCacheKey(file);
		String filename = file.getName();

		if (!installedScripts.add(cacheKey)) {
			log.debug("Dependencies already installed for script {}", filename);
			return;
		}


		if (!pythonPackageManager.isInstalled(REQUIREMENTS_ANALYZER)) {
			try {
				pythonPackageManager.install(REQUIREMENTS_ANALYZER, onOutput);
			} catch (Exception e) {
				log.warn("Pipreqs library installation failed (possibly offline). Continuing without dependency check.", e);
				return;
			}
		}
		String filenameWithoutExtension = FileNames.removeExtension(filename);
		Path tempDirectory = Files.createTempDirectory(filenameWithoutExtension);
		File copyOfScript = new File(tempDirectory.toFile(), filename);
		Files.copy(file.toPath(), copyOfScript.toPath());

		try {
			dependencyResolver.generateRequirementsFile(tempDirectory, onOutput);
			dependencyResolver.installDependenciesFromRequirements(tempDirectory, onOutput);
			onOutput.accept("Dependencies installed successfully for script " + filename);
		} catch (IOException | CommandExecutionException e) {
			log.warn("Dependency installation failed (possibly offline): {}", e.getMessage(), e);
			installedScripts.remove(cacheKey);
		} catch (InterruptedException e) {
			log.warn("Dependency installation was interrupted", e);
			installedScripts.remove(cacheKey);
			Thread.currentThread().interrupt();
		} finally {
			cleanupTempDirectory(tempDirectory, filename);
		}
	}

	private String generateCacheKey(File file) {
		return String.format("%s:%d:%d",
				file.getAbsolutePath(),
				file.lastModified(),
				file.length());
	}

	private void cleanupTempDirectory(Path tempDirectory, String scriptFilename) {
		try (var paths = Files.walk(tempDirectory)) {
			paths.sorted(Comparator.reverseOrder())
					.forEach(path -> {
						try {
							Files.delete(path);
						} catch (IOException e) {
							log.warn("Failed to delete {}: {}", path, e.getMessage());
						}
					});
		} catch (IOException e) {
			log.warn("Failed to cleanup temporary directory for script {}: {}", scriptFilename, e.getMessage());
		}
	}

}

package com.ugcs.geohammer.service.script.dependecies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;
import com.ugcs.geohammer.util.FileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PythonDependenciesInstaller {

	private static final Logger log = LoggerFactory.getLogger(PythonDependenciesInstaller.class);

	private static final String DEPENDENCY_ANALYZER_PACKAGE_NAME = "pipreqs";

	private final DependencyResolver dependencyResolver;

	private final PythonPackageManager pythonPackageManager;

	private final Set<String> installedScripts = ConcurrentHashMap.newKeySet();

	public PythonDependenciesInstaller(ExecutorService executorService,
									   PythonExecutorPathResolver pythonExecutorPathResolver) {
		this.dependencyResolver = new PipreqsDependencyResolver(executorService, pythonExecutorPathResolver);
		this.pythonPackageManager = new PythonPackageManager(pythonExecutorPathResolver, executorService);
	}

	/**
	 * Installs the required Python dependencies for the given script file if they are not already installed.
	 *
	 * @param file           the script file
	 * @param onOutput       a consumer to handle output messages
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if the operation is interrupted
	 */
	public void installIfNeeded(File file, Consumer<String> onOutput) throws IOException, InterruptedException {
		String cacheKey = generateCacheKey(file);

		String filename = file.getName();

		if (installedScripts.contains(cacheKey)) {
			log.debug("Dependencies already installed for script {}", filename);
			return;
		}


		if (!pythonPackageManager.isInstalled(DEPENDENCY_ANALYZER_PACKAGE_NAME)) {
			try {
				pythonPackageManager.install(DEPENDENCY_ANALYZER_PACKAGE_NAME, onOutput);
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
			installedScripts.add(cacheKey);
			onOutput.accept("Dependencies installed successfully for script " + filename);
		} catch (Exception e) {
			log.warn("Dependency installation failed (possibly offline). Assuming dependencies are already installed.", e);
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

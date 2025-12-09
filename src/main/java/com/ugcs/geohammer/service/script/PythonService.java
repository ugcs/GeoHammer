package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import com.ugcs.geohammer.util.PythonLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PythonService {

	private static final Logger log = LoggerFactory.getLogger(PythonService.class);

	private static final String REQUIREMENTS_ANALYZER = "pipreqs";

	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	private final CommandExecutor commandExecutor;

	private final PythonConfig pythonConfig;

	private final Set<String> installedDependenciesCache = ConcurrentHashMap.newKeySet();

	public PythonService(CommandExecutor commandExecutor, PythonConfig pythonConfig) {
		this.commandExecutor = commandExecutor;
		this.pythonConfig = pythonConfig;
	}

	/**
	 * Installs the required Python dependencies for the given script file if they are not already installed.
	 *
	 * @param scriptFile the script file
	 * @param onOutput a consumer to handle output messages
	 * @throws IOException if an I/O error occurs
	 * @throws InterruptedException if the operation is interrupted
	 */
	public void installDependencies(File scriptFile, Consumer<String> onOutput) throws IOException, InterruptedException {
		String cacheKey = generateCacheKey(scriptFile);
		String filename = scriptFile.getName();

		if (!installedDependenciesCache.add(cacheKey)) {
			log.debug("Dependencies already installed for script {}", filename);
			return;
		}


		if (!isPackageInstalled(REQUIREMENTS_ANALYZER)) {
			try {
				installPackage(REQUIREMENTS_ANALYZER, onOutput);
			} catch (Exception e) {
				log.warn("Pipreqs library installation failed (possibly offline). Continuing without dependency check.", e);
				return;
			}
		}
		String filenameWithoutExtension = FileNames.removeExtension(filename);
		Path tempDirectory = Files.createTempDirectory(filenameWithoutExtension);
		File copyOfScript = new File(tempDirectory.toFile(), filename);
		Files.copy(scriptFile.toPath(), copyOfScript.toPath());

		try {
			generateRequirementsFile(tempDirectory, onOutput);
			installDependenciesFromRequirements(tempDirectory, onOutput);
			onOutput.accept("Dependencies installed successfully for script " + filename);
		} catch (IOException | CommandExecutionException e) {
			log.warn("Dependency installation failed (possibly offline): {}", e.getMessage(), e);
			installedDependenciesCache.remove(cacheKey);
		} catch (InterruptedException e) {
			log.warn("Dependency installation was interrupted", e);
			installedDependenciesCache.remove(cacheKey);
			Thread.currentThread().interrupt();
		} finally {
			cleanupTempDirectory(tempDirectory, filename);
		}
	}

	public boolean isPackageInstalled(String packageName) throws InterruptedException, IOException {
		String pythonExecutorPath = getPythonExecutorPath().toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "show", packageName);

		try {
			commandExecutor.executeCommand(command, null);
			return true;
		} catch (CommandExecutionException e) {
			return false;
		}
	}

	public void installPackage(String packageName, Consumer<String> onOutput) throws InterruptedException, IOException {
		String pythonExecutorPath = getPythonExecutorPath().toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "install", packageName);
		commandExecutor.executeCommand(command, onOutput);
	}

	public Path getPythonExecutorPath() throws InterruptedException {
		String pythonPath = pythonConfig.getPythonExecutorPath();
		if (pythonPath == null || pythonPath.isEmpty()) {
			try {
				Future<String> future = executorService.submit(PythonLocator::getPythonExecutorPath);
				pythonPath = future.get();
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		return Path.of(pythonPath);
	}

	private String generateCacheKey(File file) {
		return String.format("%s:%d:%d",
				file.getAbsolutePath(),
				file.lastModified(),
				file.length());
	}

	private void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path pythonDir = getPythonExecutorPath().getParent();

		String pipreqsExecutable = OperatingSystemUtils.toExecutableName("pipreqs");
		Path pipreqsPath = pythonDir.resolve(OperatingSystemUtils.getScriptsDirectory()).resolve(pipreqsExecutable);

		String pipreqsCommand;
		if (Files.exists(pipreqsPath)) {
			pipreqsCommand = pipreqsPath.toString();
		} else {
			pipreqsCommand = pipreqsExecutable;
		}

		List<String> command = List.of(
				pipreqsCommand,
				directory.toString(),
				"--mode",
				"no-pin"
		);
		commandExecutor.executeCommand(command, onOutput);
	}

	private void installDependenciesFromRequirements(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path requirementsPath = directory.resolve("requirements.txt");
		if (Files.exists(requirementsPath)) {
			String pythonExecutorPath = getPythonExecutorPath().toString();
			List<String> command = List.of(
					pythonExecutorPath,
					"-m",
					"pip",
					"install",
					"-r",
					requirementsPath.toString()
			);
			commandExecutor.executeCommand(command, onOutput);
		} else {
			log.warn("No requirements.txt found in {}, skipping dependency installation.", directory);
		}
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

package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.PythonLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PythonDependenciesInstaller {

	private static final Logger log = LoggerFactory.getLogger(PythonDependenciesInstaller.class);

	private final PythonConfig pythonConfig;
	private final Set<String> installedScripts = ConcurrentHashMap.newKeySet();

	public PythonDependenciesInstaller(PythonConfig pythonConfig) {
		this.pythonConfig = pythonConfig;
	}

	/**
	 * Installs the required Python dependencies for the given script file if they are not already installed.
	 *
	 * @param file           the script file
	 * @param scriptMetadata metadata of the script
	 * @param executor       an executor service for running commands
	 * @param onOutput       a consumer to handle output messages
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if the operation is interrupted
	 */
	public void installIfNeeded(File file, ScriptMetadata scriptMetadata, ExecutorService executor, Consumer<String> onOutput) throws IOException, InterruptedException {
		String cacheKey = generateCacheKey(file);

		if (installedScripts.contains(cacheKey)) {
			log.debug("Dependencies already installed for script {}", scriptMetadata.filename());
			return;
		}


		if (!isPipreqsInstalled(executor)) {
			try {
				installPipreqs(executor, onOutput);
			} catch (Exception e) {
				log.warn("Pipreqs library installation failed (possibly offline). Continuing without dependency check.", e);
				return;
			}
		}
		String filenameWithoutExtension = FileNames.removeExtension(scriptMetadata.filename());
		Path tempDirectory = Files.createTempDirectory(filenameWithoutExtension);
		File copyOfScript = new File(tempDirectory.toFile(), scriptMetadata.filename());
		Files.copy(file.toPath(), copyOfScript.toPath());

		try {
			generateRequirements(executor, tempDirectory, onOutput);
			installDependenciesFromRequirements(executor, tempDirectory, onOutput);
			installedScripts.add(cacheKey);
			onOutput.accept("Dependencies installed successfully for script " + scriptMetadata.filename());
		} catch (Exception e) {
			log.warn("Dependency installation failed (possibly offline). Assuming dependencies are already installed.", e);
		} finally {
			cleanupTempDirectory(tempDirectory, scriptMetadata.filename());
		}
	}

	private String generateCacheKey(File file) {
		String absolutePath = file.getAbsolutePath();
		long lastModified = file.lastModified();
		long fileSize = file.length();
		return absolutePath + ":" + lastModified + ":" + fileSize;
	}

	private boolean isPipreqsInstalled(ExecutorService executorService) throws InterruptedException, IOException {
		List<String> command = List.of(getPythonExecutablePath(executorService), "-m", "pip", "show", "pipreqs");
		Process process = new ProcessBuilder(command).start();
		int exitCode = process.waitFor();
		return exitCode == 0;
	}

	private void installPipreqs(ExecutorService executorService, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		List<String> command = List.of(getPythonExecutablePath(executorService), "-m", "pip", "install", "pipreqs");
		executeCommand(command, onOutput);
	}

	private void generateRequirements(ExecutorService executorService, Path workingDirectory, Consumer<String> onOutput) throws IOException, InterruptedException {
		String pythonPath = getPythonExecutablePath(executorService);
		Path pythonDir = Path.of(pythonPath).getParent();

		String pipreqsExecutable = isWindows() ? "pipreqs.exe" : "pipreqs";
		Path pipreqsPath = pythonDir.resolve(isWindows() ? "Scripts" : "bin").resolve(pipreqsExecutable);

		String pipreqsCommand;
		if (Files.exists(pipreqsPath)) {
			pipreqsCommand = pipreqsPath.toString();
		} else {
			pipreqsCommand = pipreqsExecutable;
		}

		List<String> command = List.of(
				pipreqsCommand,
				workingDirectory.toString(),
				"--mode",
				"no-pin"
		);
		executeCommand(command, onOutput);
	}

	private boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

	private void installDependenciesFromRequirements(ExecutorService executorService, Path workingDirectory,
													 Consumer<String> onOutput) throws IOException, InterruptedException {
		Path requirementsFile = workingDirectory.resolve("requirements.txt");
		if (Files.exists(requirementsFile)) {
			List<String> command = List.of(
					getPythonExecutablePath(executorService),
					"-m",
					"pip",
					"install",
					"-r",
					requirementsFile.toString()
			);
			executeCommand(command, onOutput);
		} else {
			log.warn("No requirements.txt found in {}, skipping dependency installation.", workingDirectory);
		}
	}

	private void executeCommand(List<String> command, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		log.debug("Executing command: {}", String.join(" ", command));
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);

		Process process = processBuilder.start();

		if (onOutput != null) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					onOutput.accept(line);
				}
			}
		}

		int exitCode = process.waitFor();
		if (exitCode != 0) {
			log.error("Process failed with exit code {}", exitCode);
			throw new ScriptException(exitCode);
		}
	}

	private String getPythonExecutablePath(ExecutorService executor) throws InterruptedException {
		String pythonPath = pythonConfig.getPythonExecutorPath();
		if (pythonPath == null || pythonPath.isEmpty()) {
			try {
				Future<String> future = executor.submit(PythonLocator::getPythonExecutorPath);
				pythonPath = future.get();
			} catch (ExecutionException e) {
				throw new RuntimeException(e);
			}
		}
		return pythonPath;
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

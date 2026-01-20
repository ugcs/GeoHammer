package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
public class PythonService {

	private static final Logger log = LoggerFactory.getLogger(PythonService.class);

	private static final String PREF_PYTHON_EXECUTOR = "python";

	private static final String PREF_PYTHON_EXECUTOR_PATH = "path";

	private static final String REQUIREMENTS_ANALYZER = "pipreqs";

	private static final String REQUIREMENTS_FILE = "requirements.txt";

	private final CommandExecutor commandExecutor;

	private final PrefSettings prefSettings;

	public PythonService(CommandExecutor commandExecutor, PrefSettings prefSettings) {
		this.commandExecutor = commandExecutor;
		this.prefSettings = prefSettings;
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
		String filename = scriptFile.getName();

		if (!isPackageInstalled(REQUIREMENTS_ANALYZER)) {
			try {
				installPackage(REQUIREMENTS_ANALYZER, onOutput);
			} catch (Exception e) {
				log.warn("Requirements analyzer library installation failed (possibly offline). Continuing without dependency check.", e);
				return;
			}
		}

		String filenameWithoutExtension = FileNames.removeExtension(filename);
		Path tempDirectory = Files.createTempDirectory(filenameWithoutExtension);
		File copyOfScript = new File(tempDirectory.toFile(), filename);
		Files.copy(scriptFile.toPath(), copyOfScript.toPath());

		try {
			generateRequirementsFile(tempDirectory, onOutput);

			if (!Files.exists(tempDirectory.resolve(REQUIREMENTS_FILE))) {
				onOutput.accept("No dependencies found for script " + filename);
				return;
			}

			if (areAllPackagesInstalled(tempDirectory)) {
				onOutput.accept("Dependencies already installed for script " + filename);
				return;
			}

			installDependenciesFromRequirements(tempDirectory, onOutput);
			onOutput.accept("Dependencies installed successfully for script " + filename);
		} catch (IOException | CommandExecutionException e) {
			log.warn("Dependency installation failed (possibly offline): {}", e.getMessage(), e);
		} catch (InterruptedException e) {
			log.warn("Dependency installation was interrupted", e);
			Thread.currentThread().interrupt();
		} finally {
			cleanupTempDirectory(tempDirectory, filename);
		}
	}

	private boolean areAllPackagesInstalled(Path directory) throws IOException, InterruptedException {
		Path requirementsPath = directory.resolve(REQUIREMENTS_FILE);
		List<String> requiredPackages = Files.readAllLines(requirementsPath).stream()
				.map(String::trim)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.map(line -> line.split("[=<>~!]")[0].trim())
				.toList();

		for (String packageName : requiredPackages) {
			if (!canImportPackage(packageName)) {
				return false;
			}
		}
		return true;
	}

	private boolean canImportPackage(String packageName) throws InterruptedException, IOException {
		String pythonExecutorPath = getPythonPath().toString();
		String importCommand = String.format("import %s", packageName);
		List<String> command = List.of(pythonExecutorPath, "-c", importCommand);

		try {
			commandExecutor.executeCommand(command, null);
			return true;
		} catch (CommandExecutionException e) {
			return false;
		}
	}

	public boolean isPackageInstalled(String packageName) throws InterruptedException, IOException {
		String pythonExecutorPath = getPythonPath().toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "show", packageName);

		try {
			commandExecutor.executeCommand(command, null);
			return true;
		} catch (CommandExecutionException e) {
			return false;
		}
	}

	public void installPackage(String packageName, Consumer<String> onOutput) throws InterruptedException, IOException {
		String pythonExecutorPath = getPythonPath().toString();
		List<String> command = List.of(pythonExecutorPath, "-m", "pip", "install", packageName);
		commandExecutor.executeCommand(command, onOutput);
	}

	public Path getPythonPath() throws IOException {
		String pythonPath = prefSettings.getString(PREF_PYTHON_EXECUTOR, PREF_PYTHON_EXECUTOR_PATH);
		if (pythonPath == null || pythonPath.isEmpty()) {
			String[] command;
			if (OperatingSystemUtils.isWindows()) {
				command = new String[]{"where", "python"};
			} else {
				command = new String[]{"which", "python3"};
			}
			Process process = new ProcessBuilder(command).start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				pythonPath = reader.readLine();
			}
		}
		if (pythonPath == null || pythonPath.isEmpty()) {
			throw new IllegalStateException("Python path is not found.");
		}
		return Path.of(pythonPath);
	}

	public void setPythonPath(@Nullable String pythonPath) {
		if (pythonPath != null && !pythonPath.isEmpty()) {
			prefSettings.setValue(PREF_PYTHON_EXECUTOR, PREF_PYTHON_EXECUTOR_PATH, pythonPath);
		}
	}

	private Path getPipreqsPath() throws IOException, InterruptedException {
		Path pipreqsPath = findPipreqsExecutable();
		if (pipreqsPath != null) {
			return pipreqsPath;
		}
		throw new IllegalStateException("Requirements analyzer package (pipreqs) not found. Please install it manually");
	}

	private @Nullable Path findPipreqsExecutable() throws IOException, InterruptedException {
		String pythonExecutorPath = getPythonPath().toString();
		String script = "import shutil; print(shutil.which('" + REQUIREMENTS_ANALYZER + "') or '')";
		List<String> command = List.of(pythonExecutorPath, "-c", script);

		StringBuilder output = new StringBuilder();
		Consumer<String> outputConsumer = line -> {
			if (output.isEmpty()) {
				output.append(line);
			}
		};

		commandExecutor.executeCommand(command, outputConsumer);

		String pathStr = output.toString().trim();
		if (!pathStr.isEmpty()) {
			return Paths.get(pathStr);
		}
		return null;
	}

	private void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path pipreqsPath = getPipreqsPath();

		String pipreqsCommand;
		if (pipreqsPath != null && Files.exists(pipreqsPath)) {
			pipreqsCommand = pipreqsPath.toString();
		} else {
			throw new IllegalStateException("pipreqs not found in path: " + pipreqsPath);
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
		Path requirementsPath = directory.resolve(REQUIREMENTS_FILE);
		if (!Files.exists(requirementsPath)) {
			log.warn("No requirements.txt found in {}, skipping dependency installation.", directory);
			return;
		}

		String pythonExecutorPath = getPythonPath().toString();
		List<String> command = List.of(
				pythonExecutorPath,
				"-m",
				"pip",
				"install",
				"-r",
				requirementsPath.toString()
		);
		commandExecutor.executeCommand(command, onOutput);
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

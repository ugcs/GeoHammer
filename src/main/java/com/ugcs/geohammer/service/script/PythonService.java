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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.ugcs.geohammer.PrefSettings;
import com.ugcs.geohammer.util.FileNames;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.jspecify.annotations.Nullable;

@Service
public class PythonService {

	private static final Logger log = LoggerFactory.getLogger(PythonService.class);

	private static final String PREF_PYTHON_EXECUTOR = "python";

	private static final String PREF_PYTHON_EXECUTOR_PATH = "path";

	private static final String REQUIREMENTS_ANALYZER = "pipreqs";

	private static final String REQUIREMENTS_FILE = "requirements.txt";

	private static final PythonVersion MINIMAL_PYTHON_VERSION = PythonVersion.parse("3.8");

	private static final String INSTALL_PYTHON_HINT =
			"Please install Python v" + MINIMAL_PYTHON_VERSION
					+ " or higher. Download: https://www.python.org/downloads/";

	private static final String MISSING_IMPORTS_PREFIX = "MISSING:";

	private final CommandExecutor commandExecutor;

	private final PrefSettings prefSettings;

	private final ScriptPaths scriptPaths;

	public PythonService(CommandExecutor commandExecutor, PrefSettings prefSettings, ScriptPaths scriptPaths) {
		this.commandExecutor = commandExecutor;
		this.prefSettings = prefSettings;
		this.scriptPaths = scriptPaths;
	}

	public void installDependencies(File scriptFile, Consumer<String> onOutput)
			throws IOException, InterruptedException, DependencyImportException {
		applyDependencies(scriptFile, onOutput, false);
	}

	public void reinstallDependencies(File scriptFilePath, Consumer<String> onOutput)
			throws IOException, InterruptedException, DependencyImportException {
		applyDependencies(scriptFilePath, onOutput, true);
	}

	private void applyDependencies(File scriptFile,
			Consumer<String> onOutput, boolean forceReinstall)
			throws IOException, InterruptedException, DependencyImportException {
		String filename = scriptFile.getName();

		File checkImportsScript = scriptPaths.getCheckImportsScript().toFile();
		if (!forceReinstall) {
			try {
				checkImports(scriptFile, checkImportsScript);
				onOutput.accept("Dependencies already satisfied for script " + filename);
				return;
			} catch (DependencyImportException e) {
				log.debug("Initial import check failed: {}", e.getMessage());
			}
		}

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

			boolean hasRequirements = Files.exists(tempDirectory.resolve(REQUIREMENTS_FILE));
			if (!hasRequirements) {
				onOutput.accept("No dependencies found for script " + filename);
			} else if (forceReinstall) {
				reinstallDependenciesFromRequirements(tempDirectory, onOutput);
			} else {
				installDependenciesFromRequirements(tempDirectory, onOutput);
			}

			checkImports(scriptFile, checkImportsScript);
			if (hasRequirements) {
				onOutput.accept(forceReinstall
						? "Dependencies reinstalled for script " + filename
						: "Dependencies installed for script " + filename);
			}
		} finally {
			cleanupTempDirectory(tempDirectory, filename);
		}
	}

	private void checkImports(File scriptFile, File checkImportsScript) throws IOException, InterruptedException, DependencyImportException {
		if (!checkImportsScript.exists()) {
			throw new IllegalStateException("Import check script not found: " + checkImportsScript.getAbsolutePath());
		}
		List<String> command = List.of(
				getPythonPath().toString(),
				checkImportsScript.getAbsolutePath(),
				scriptFile.getAbsolutePath()
		);
		AtomicReference<String> missingModule = new AtomicReference<>();
		StringBuilder outputBuffer = new StringBuilder();
		try {
			commandExecutor.executeCommand(command, line -> {
				if (!outputBuffer.isEmpty()) {
					outputBuffer.append(System.lineSeparator());
				}
				outputBuffer.append(line);
				if (line.startsWith(MISSING_IMPORTS_PREFIX)) {
					String name = line.substring(MISSING_IMPORTS_PREFIX.length()).trim();
					if (!name.isEmpty()) {
						missingModule.compareAndSet(null, name);
					}
				}
			});
		} catch (CommandExecutionException e) {
			String missing = missingModule.get();
			if (missing != null) {
				throw new DependencyImportException(missing);
			}
			String output = outputBuffer.toString().trim();
			String message = output.isEmpty()
					? "Import check script failed (exit code " + e.getExitCode() + ")"
					: "Import check script failed:" + System.lineSeparator() + output;
			throw new IllegalStateException(message);
		}
	}

	private void reinstallDependenciesFromRequirements(Path directory, Consumer<String> onOutput)
			throws IOException, InterruptedException {
		Path requirementsPath = directory.resolve(REQUIREMENTS_FILE);
		List<String> command = List.of(
				getPythonPath().toString(),
				"-m", "pip", "install",
				"--force-reinstall",
				"--no-cache-dir",
				"-r", requirementsPath.toString()
		);
		commandExecutor.executeCommand(command, onOutput);
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
			throw new IllegalStateException("Python executable not found. Please install Python or configure path in settings.");
		}
		return Path.of(pythonPath);
	}

	public void setPythonPath(@Nullable String pythonPath) {
		if (pythonPath != null && !pythonPath.isEmpty()) {
			prefSettings.setValue(PREF_PYTHON_EXECUTOR, PREF_PYTHON_EXECUTOR_PATH, pythonPath);
		}
	}

	private Path getPipreqsPath() throws IOException, InterruptedException {
		Path pythonPath = getPythonPath();

		Path pipreqsPath = findPipreqsInPythonDirectory(pythonPath);
		if (pipreqsPath != null && Files.exists(pipreqsPath)) {
			return pipreqsPath;
		}

		pipreqsPath = findPipreqsFromPackageLocation();
		if (pipreqsPath != null) {
			return pipreqsPath;
		}

		throw new IllegalStateException(
				"Requirements analyzer (pipreqs) not found. Install it manually by running: \""
						+ pythonPath + "\" -m pip install pipreqs");
	}

	private @Nullable Path findPipreqsInPythonDirectory(Path pythonPath) {
		Path parent = pythonPath.getParent();
		if (parent == null) {
			return null;
		}
		if (OperatingSystemUtils.isWindows()) {
			return parent.resolve(Paths.get("Scripts", "pipreqs.exe"));
		}
		return parent.resolve("pipreqs");
	}

	private @Nullable Path findPipreqsFromPackageLocation() throws IOException, InterruptedException {
		String pythonExecutable = getPythonPath().toString();
		List<String> command = List.of(pythonExecutable, "-m", "pip", "show", REQUIREMENTS_ANALYZER);

		StringBuilder output = new StringBuilder();
		Consumer<String> outputConsumer = line -> output.append(line).append("\n");
		commandExecutor.executeCommand(command, outputConsumer);

		String location = null;
		for (String line : output.toString().split("\n")) {
			if (line.startsWith("Location:")) {
				location = line.substring("Location:".length()).trim();
				break;
			}
		}

		if (location == null || location.isEmpty()) {
			return null;
		}

		Path sitePackages = Paths.get(location);

		if (OperatingSystemUtils.isWindows()) {
			Path scriptsDir = Paths.get("Scripts", "pipreqs.exe");
			Path parent = sitePackages.getParent();
			if (parent == null) {
				return null;
			}
			Path pipreqsPath = parent.resolve(scriptsDir);
			if (Files.exists(pipreqsPath)) {
				return pipreqsPath;
			}
		} else {
			Path pipreqsPath = sitePackages.getParent().resolve("pipreqs");
			if (Files.exists(pipreqsPath)) {
				return pipreqsPath;
			}
		}
		return null;
	}

	private void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path pipreqsPath = getPipreqsPath();
		List<String> command = List.of(
				pipreqsPath.toString(),
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

	public void checkVersion() throws InterruptedException {
		try {
			String version = getVersion();
			PythonVersion pythonVersion = PythonVersion.parse(version);
			if (pythonVersion.compareTo(MINIMAL_PYTHON_VERSION) < 0) {
				throw new IllegalStateException(
						"Python version " + pythonVersion + " is not supported. " + INSTALL_PYTHON_HINT);
			}
		} catch (CommandExecutionException | IOException e) {
			throw new IllegalStateException(
					"Python isn't installed on your PC. " + INSTALL_PYTHON_HINT, e);
		}
	}

	private String getVersion() throws IOException, InterruptedException {
		StringBuilder output = new StringBuilder();
		commandExecutor.executeCommand(
				List.of(getPythonPath().toString(), "--version"),
				line -> output.append(line).append('\n'));
		return output.toString().trim();
	}
}

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

	public void reinstallDependencies(File scriptFile, Consumer<String> onOutput)
			throws IOException, InterruptedException, DependencyImportException {
		applyDependencies(scriptFile, onOutput, true);
	}

	private void applyDependencies(File scriptFile,
			Consumer<String> onOutput, boolean forceReinstall)
			throws IOException, InterruptedException, DependencyImportException {
		String filename = scriptFile.getName();

		File checkImportsScript = scriptPaths.getCheckImportsScript().toFile();
		if (!forceReinstall) {
			String failingModule = checkImports(scriptFile, checkImportsScript);
			if (failingModule == null) {
				onOutput.accept("Dependencies already satisfied for script " + filename);
				return;
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

			String failingModule = checkImports(scriptFile, checkImportsScript);
			if (failingModule != null) {
				throw new DependencyImportException(failingModule);
			}
			if (hasRequirements) {
				onOutput.accept(forceReinstall
						? "Dependencies reinstalled for script " + filename
						: "Dependencies installed for script " + filename);
			}
		} finally {
			cleanupTempDirectory(tempDirectory, filename);
		}
	}

	@Nullable
	private String checkImports(File scriptFile, File checkImportsScript) throws IOException, InterruptedException {
		if (!checkImportsScript.exists()) {
			throw new IOException("Import check script not found: " + checkImportsScript.getAbsolutePath());
		}
		List<String> command = List.of(
				getPythonPath().toString(),
				checkImportsScript.getAbsolutePath(),
				scriptFile.getAbsolutePath()
		);
		AtomicReference<String> failingModule = new AtomicReference<>();
		try {
			commandExecutor.executeCommand(command, line -> {
				if (!line.isBlank()) {
					failingModule.compareAndSet(null, line.trim());
				}
			});
			return null;
		} catch (CommandExecutionException e) {
			return failingModule.get();
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
		Path pythonPath = getPythonPath();

		Path pipreqsPath = findPipreqsInPythonDirectory(pythonPath);
		if (pipreqsPath != null && Files.exists(pipreqsPath)) {
			return pipreqsPath;
		}

		pipreqsPath = findPipreqsFromPackageLocation();
		if (pipreqsPath != null) {
			return pipreqsPath;
		}

		throw new IllegalStateException("Requirements analyzer package (pipreqs) not found. Please install it manually");
	}

	private Path findPipreqsInPythonDirectory(Path pythonPath) {
		Path pipreqsPath;
		if (OperatingSystemUtils.isWindows()) {
			pipreqsPath = pythonPath.getParent().resolve(Paths.get("Scripts", "pipreqs.exe"));
		} else {
			pipreqsPath = pythonPath.getParent().resolve("pipreqs");
		}
		return pipreqsPath;
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
			Path pipreqsPath = sitePackages.getParent().resolve(scriptsDir);
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
		if (pipreqsPath == null || !Files.exists(pipreqsPath)) {
			throw new IllegalStateException("pipreqs not found in path: " + pipreqsPath);
		}
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
}

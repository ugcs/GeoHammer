package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.ugcs.geohammer.util.FileNames;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScriptRequirements {

	private static final Logger log = LoggerFactory.getLogger(ScriptRequirements.class);

	private static final String ANALYZER = "pipreqs";

	private static final String ANALYZER_MODULE = "pipreqs.pipreqs";

	private static final String FILE_SUFFIX = "_requirements.txt";

	private static final String PYTHON_EXTENSION = "py";

	private static final String PACKAGE_INIT = "__init__.py";

	private static final Pattern REQUIREMENT_NAME = Pattern.compile("\\s*([A-Za-z0-9][A-Za-z0-9._-]*)");

	private static final Pattern NAME_SEPARATORS = Pattern.compile("[-_.]+");

	private final CommandExecutor commandExecutor;

	private final PythonInterpreter interpreter;

	private final PackageInstaller packageInstaller;

	public ScriptRequirements(CommandExecutor commandExecutor, PythonInterpreter interpreter,
			PackageInstaller packageInstaller) {
		this.commandExecutor = commandExecutor;
		this.interpreter = interpreter;
		this.packageInstaller = packageInstaller;
	}

	@Nullable
	public Path generateIfMissing(File scriptFile, Consumer<String> onOutput) throws InterruptedException {
		Path path = getRequirementsFile(scriptFile);
		if (Files.exists(path)) {
			onOutput.accept("Using requirements file " + path.getFileName());
			return path;
		}
		try {
			return generate(scriptFile, onOutput);
		} catch (IOException | RuntimeException e) {
			log.warn("Failed to create requirements file for script {}", scriptFile.getName(), e);
			onOutput.accept("Could not create requirements file for script " + scriptFile.getName());
			return null;
		}
	}

	public static List<String> excludeLocalModules(List<String> requirements, Path scriptDirectory)
			throws IOException {
		Set<String> localModules = getLocalModules(scriptDirectory);
		List<String> kept = new ArrayList<>(requirements.size());
		for (String requirement : requirements) {
			Matcher matcher = REQUIREMENT_NAME.matcher(requirement);
			if (matcher.lookingAt() && localModules.contains(normalizeName(matcher.group(1)))) {
				continue;
			}
			kept.add(requirement);
		}
		return kept;
	}

	@Nullable
	private Path generate(File scriptFile, Consumer<String> onOutput) throws IOException, InterruptedException {
		if (!packageInstaller.isInstalled(ANALYZER)) {
			try {
				packageInstaller.install(ANALYZER, onOutput);
			} catch (Exception e) {
				log.warn("Requirements analyzer library installation failed (possibly offline).", e);
				onOutput.accept("Could not obtain " + ANALYZER + ", skipping dependency check");
				return null;
			}
		}
		Path path = getRequirementsFile(scriptFile);
		runAnalyzer(scriptFile, path, onOutput);
		return path;
	}

	private Path getRequirementsFile(File scriptFile) {
		String filenameWithoutExtension = FileNames.removeExtension(scriptFile.getName());
		return scriptFile.toPath().resolveSibling(filenameWithoutExtension + FILE_SUFFIX);
	}

	private void runAnalyzer(File scriptFile, Path requirementsPath, Consumer<String> onOutput)
			throws IOException, InterruptedException {
		String filename = scriptFile.getName();
		Path tempDirectory = Files.createTempDirectory(FileNames.removeExtension(filename));
		try {
			Files.copy(scriptFile.toPath(), tempDirectory.resolve(filename));
			List<String> command = List.of(
					interpreter.getPath().toString(),
					"-m",
					ANALYZER_MODULE,
					tempDirectory.toString(),
					"--encoding",
					"utf-8",
					"--mode",
					"no-pin",
					"--savepath",
					requirementsPath.toString()
			);
			commandExecutor.executeCommand(command, tempDirectory.toFile(), onOutput);
			// the analyzer sees only the script copy, so modules next to the original are reported as packages
			List<String> requirements = Files.readAllLines(requirementsPath);
			Files.write(requirementsPath, excludeLocalModules(requirements, scriptFile.toPath().getParent()));
		} finally {
			cleanupTempDirectory(tempDirectory, filename);
		}
	}

	private static Set<String> getLocalModules(Path directory) throws IOException {
		Set<String> modules = new HashSet<>();
		try (Stream<Path> paths = Files.list(directory)) {
			for (Path path : (Iterable<Path>) paths::iterator) {
				String filename = path.getFileName().toString();
				if (Files.isRegularFile(path) && PYTHON_EXTENSION.equals(FileNames.getExtension(filename))) {
					modules.add(normalizeName(FileNames.removeExtension(filename)));
				} else if (Files.isRegularFile(path.resolve(PACKAGE_INIT))) {
					modules.add(normalizeName(filename));
				}
			}
		}
		return modules;
	}

	// PEP 503 normalization: case and runs of separators are insignificant
	private static String normalizeName(String name) {
		return NAME_SEPARATORS.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-");
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

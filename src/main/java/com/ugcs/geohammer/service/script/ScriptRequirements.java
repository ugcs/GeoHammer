package com.ugcs.geohammer.service.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.ugcs.geohammer.util.Check;
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

	private static final String FILE_NAME = "requirements.txt";

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
	public Requirements derive(File scriptFile, Consumer<String> onOutput)
			throws IOException, InterruptedException {
		if (!packageInstaller.isInstalled(ANALYZER)) {
			try {
				packageInstaller.install(ANALYZER, onOutput);
			} catch (Exception e) {
				log.warn("Requirements analyzer library installation failed (possibly offline)."
						+ " Continuing without dependency check.", e);
				return null;
			}
		}

		String filename = scriptFile.getName();
		Requirements requirements = new Requirements(
				Files.createTempDirectory(FileNames.removeExtension(filename)));
		try {
			Files.copy(scriptFile.toPath(), requirements.directory.resolve(filename));
			List<String> command = List.of(
					interpreter.getPath().toString(),
					"-m",
					ANALYZER_MODULE,
					requirements.directory.toString(),
					"--encoding",
					"utf-8",
					"--mode",
					"no-pin"
			);
			commandExecutor.executeCommand(command, requirements.directory.toFile(), onOutput);
			return requirements;
		} catch (IOException | InterruptedException | RuntimeException e) {
			requirements.close();
			throw e;
		}
	}

	public static final class Requirements implements AutoCloseable {

		private final Path directory;

		private Requirements(Path directory) {
			Check.notNull(directory);
			this.directory = directory;
		}

		@Nullable
		public Path path() {
			Path path = directory.resolve(FILE_NAME);
			return Files.exists(path) ? path : null;
		}

		@Override
		public void close() {
			try (var paths = Files.walk(directory)) {
				paths.sorted(Comparator.reverseOrder())
						.forEach(path -> {
							try {
								Files.delete(path);
							} catch (IOException e) {
								log.warn("Failed to delete {}: {}", path, e.getMessage());
							}
						});
			} catch (IOException e) {
				log.warn("Failed to cleanup temporary directory {}: {}", directory, e.getMessage());
			}
		}
	}
}

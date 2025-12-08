package com.ugcs.geohammer.service.script.dependencies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.CommandExecutor;
import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;
import com.ugcs.geohammer.util.OperatingSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class PipreqsDependencyResolver implements DependencyResolver {

	private static final Logger log = LoggerFactory.getLogger(PipreqsDependencyResolver.class);

	private final ExecutorService executorService;

	private final PythonExecutorPathResolver pythonPathResolver;

	private final CommandExecutor commandExecutor;

	public PipreqsDependencyResolver(ExecutorService executorService,
									 PythonExecutorPathResolver pythonPathResolver,
									 CommandExecutor commandExecutor) {
		this.executorService = executorService;
		this.pythonPathResolver = pythonPathResolver;
		this.commandExecutor = commandExecutor;
	}

	public void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path pythonDir = pythonPathResolver.getPythonExecutablePath(executorService).getParent();

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

	public void installDependenciesFromRequirements(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path requirementsPath = directory.resolve("requirements.txt");
		if (Files.exists(requirementsPath)) {
			String pythonExecutorPath = pythonPathResolver.getPythonExecutablePath(executorService).toString();
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
}

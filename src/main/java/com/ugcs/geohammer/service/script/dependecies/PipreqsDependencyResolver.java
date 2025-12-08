package com.ugcs.geohammer.service.script.dependecies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import com.ugcs.geohammer.service.script.ProcessCommandExecutor;
import com.ugcs.geohammer.service.script.PythonExecutorPathResolver;
import com.ugcs.geohammer.util.OsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class PipreqsDependencyResolver implements DependencyResolver {

	private static final Logger log = LoggerFactory.getLogger(PipreqsDependencyResolver.class);

	private final PythonExecutorPathResolver pythonExecutorPathResolver;

	private final ExecutorService executorService;

	public PipreqsDependencyResolver(ExecutorService executorService, PythonExecutorPathResolver pythonExecutorPathResolver) {
		this.executorService = executorService;
		this.pythonExecutorPathResolver = pythonExecutorPathResolver;
	}

	public void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException, InterruptedException {
		Path pythonDir = pythonExecutorPathResolver.getPath(executorService);

		String pipreqsExecutable = OsUtil.toExecutableName("pipreqs");
		Path pipreqsPath = pythonDir.resolve(OsUtil.getScriptsDirectory()).resolve(pipreqsExecutable);

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
		ProcessCommandExecutor.executeCommand(command, onOutput);
	}

	public void installDependenciesFromRequirements(Path directory, Consumer<String> onOutput) throws IOException,
			InterruptedException {
		Path requirementsPath = directory.resolve("requirements.txt");
		if (Files.exists(requirementsPath)) {
			String pythonExecutorPath = pythonExecutorPathResolver.getPath(executorService).toString();
			List<String> command = List.of(
					pythonExecutorPath,
					"-m",
					"pip",
					"install",
					"-r",
					requirementsPath.toString()
			);
			ProcessCommandExecutor.executeCommand(command, onOutput);
		} else {
			log.warn("No requirements.txt found in {}, skipping dependency installation.", directory);
		}
	}
}

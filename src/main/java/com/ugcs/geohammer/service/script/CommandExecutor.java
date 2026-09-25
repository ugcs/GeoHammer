package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import com.ugcs.geohammer.util.Check;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CommandExecutor {

	private static final Logger log = LoggerFactory. getLogger(CommandExecutor. class);

	private final ExecutorService executor;

	public CommandExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public void executeCommand(List<String> command, @Nullable Consumer<String> onOutput) throws IOException, InterruptedException {
		executeCommand(command, null, onOutput);
	}

	public void executeCommand(List<String> command, @Nullable File workingDirectory, @Nullable Consumer<String> onOutput)
			throws IOException, InterruptedException {
		Check.notEmpty(command);

		log.debug("Executing command: {}", String.join(" ", command));
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		if (workingDirectory != null) {
			processBuilder.directory(workingDirectory);
		}
		processBuilder.redirectErrorStream(true);
		if (onOutput == null) {
			// output nobody reads must not fill the pipe and block the process
			processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
		}

		Process process = processBuilder.start();
		try {
			if (onOutput != null) {
				Future<Void> reading = executor.submit(() -> {
					readOutput(process, onOutput);
					return null;
				});
				// throws on a reading failure
				awaitOutput(reading);
			}
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				log.error("Process failed with exit code {}", exitCode);
				throw new CommandExecutionException(exitCode);
			}
		} finally {
			if (process.isAlive()) {
				process.descendants().forEach(ProcessHandle::destroyForcibly);
				process.destroyForcibly();
				log.warn("Process forcibly destroyed: {}", String.join(" ", command));
			}
		}
	}

	private static void readOutput(Process process, Consumer<String> onOutput) throws IOException {
		Check.notNull(process);
		Check.notNull(onOutput);

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				onOutput.accept(line);
			}
		}
	}

	private static void awaitOutput(Future<Void> reading) throws IOException, InterruptedException {
		try {
			reading.get();
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException ioException) {
				throw ioException;
			}
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new RuntimeException(cause);
		}
	}
}

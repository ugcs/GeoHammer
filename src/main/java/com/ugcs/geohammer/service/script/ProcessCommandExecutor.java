package com.ugcs.geohammer.service.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class ProcessCommandExecutor {
	private static final Logger log = LoggerFactory. getLogger(ProcessCommandExecutor. class);

	public void executeCommand(List<String> command, Consumer<String> onOutput) throws IOException, InterruptedException {
		log.debug("Executing command: {}", String.join(" ", command));
		ProcessBuilder processBuilder = new ProcessBuilder(command);
		processBuilder.redirectErrorStream(true);

		Process process = processBuilder.start();

		try {
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
				throw new CommandExecutionException(exitCode);
			}
		} finally {
			if (process.isAlive()) {
				process. destroyForcibly();
				log.warn("Process forcibly destroyed: {}", String.join(" ", command));
			}
		}
	}
}

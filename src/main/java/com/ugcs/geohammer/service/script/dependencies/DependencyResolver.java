package com.ugcs.geohammer.service.script.dependencies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface DependencyResolver {
	void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException, InterruptedException;

	void installDependenciesFromRequirements(Path directory, Consumer<String> onOutput) throws IOException, InterruptedException;
}

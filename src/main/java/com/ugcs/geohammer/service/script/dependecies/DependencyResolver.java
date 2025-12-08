package com.ugcs.geohammer.service.script.dependecies;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface DependencyResolver {
	void generateRequirementsFile(Path directory, Consumer<String> onOutput) throws IOException, InterruptedException;

	void installDependenciesFromRequirements(Path directory, Consumer<String> onOutput) throws IOException, InterruptedException;
}

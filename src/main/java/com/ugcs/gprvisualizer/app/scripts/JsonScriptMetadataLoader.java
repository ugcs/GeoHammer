package com.ugcs.gprvisualizer.app.scripts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcs.gprvisualizer.app.ScriptExecutionView;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonScriptMetadataLoader implements ScriptMetadataLoader {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<ScriptExecutionView.ScriptMetadata> loadScriptMetadata(Path scriptsDir) throws IOException {
		List<ScriptExecutionView.ScriptMetadata> scriptMetadata = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsDir, "*.json")) {
			for (Path metaFile : stream) {
				ScriptExecutionView.ScriptMetadata script =
						objectMapper.readValue(metaFile.toFile(), ScriptExecutionView.ScriptMetadata.class);
				scriptMetadata.add(script);
			}
		}
		return scriptMetadata;
	}
}

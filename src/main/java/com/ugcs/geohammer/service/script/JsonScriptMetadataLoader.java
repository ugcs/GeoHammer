package com.ugcs.geohammer.service.script;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonScriptMetadataLoader implements ScriptMetadataLoader {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<ScriptMetadata> loadScriptMetadata(Path scriptsDir) throws IOException {
		List<ScriptMetadata> scriptMetadata = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsDir, "*.json")) {
			for (Path metaFile : stream) {
				ScriptMetadata script =
						objectMapper.readValue(metaFile.toFile(), ScriptMetadata.class);
				scriptMetadata.add(script);
			}
		}
		return scriptMetadata;
	}
}

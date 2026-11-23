package com.ugcs.geohammer.service.script;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcs.geohammer.util.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class JsonScriptMetadataLoader implements ScriptMetadataLoader {

	private static final Logger log = LoggerFactory.getLogger(JsonScriptMetadataLoader.class);

	private final ScriptPaths scriptPaths;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public JsonScriptMetadataLoader(ScriptPaths scriptPaths) {
		this.scriptPaths = Check.notNull(scriptPaths);
	}

	@Override
	public List<ScriptMetadata> loadScriptMetadata() throws IOException {
		Path scriptsPath = scriptPaths.getScriptsPath();
		Path userScriptsPath = scriptPaths.getUserScriptsPath();
		List<ScriptMetadata> scripts = Files.isDirectory(scriptsPath)
				? loadScriptMetadata(scriptsPath)
				: new ArrayList<>();
		if (!Files.isDirectory(userScriptsPath)) {
			return scripts;
		}
		for (ScriptMetadata userScript : loadScriptMetadata(userScriptsPath)) {
			int index = indexOf(scripts, userScript.filename());
			if (index >= 0) {
				scripts.set(index, userScript);
			} else {
				scripts.add(userScript);
			}
		}
		return scripts;
	}

	private List<ScriptMetadata> loadScriptMetadata(Path folder) throws IOException {
		List<ScriptMetadata> scripts = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.json")) {
			for (Path metaFile : stream) {
				try {
					ScriptMetadata script =
							objectMapper.readValue(metaFile.toFile(), ScriptMetadata.class);
					scripts.add(script);
				} catch (IOException e) {
					log.warn("Skipped unreadable script description {}", metaFile.getFileName(), e);
				}
			}
		}
		return scripts;
	}

	private static int indexOf(List<ScriptMetadata> scripts, String filename) {
		for (int i = 0; i < scripts.size(); i++) {
			if (Objects.equals(scripts.get(i).filename(), filename)) {
				return i;
			}
		}
		return -1;
	}
}

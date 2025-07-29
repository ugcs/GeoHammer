package com.ugcs.gprvisualizer.app.scripts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ugcs.gprvisualizer.app.PythonScriptsView;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class JsonScriptMetadataMetadataLoader implements PythonScriptMetadataLoader {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Override
	public List<PythonScriptsView.PythonScriptMetadata> loadScriptsMetadata(Path scriptsDir) throws IOException {
		List<PythonScriptsView.PythonScriptMetadata> scripts = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(scriptsDir, "*.json")) {
			for (Path metaFile : stream) {
				PythonScriptsView.PythonScriptMetadata script =
						objectMapper.readValue(metaFile.toFile(), PythonScriptsView.PythonScriptMetadata.class);
				scripts.add(script);
			}
		}
		return scripts;
	}
}

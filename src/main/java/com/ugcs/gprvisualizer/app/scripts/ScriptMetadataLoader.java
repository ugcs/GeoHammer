package com.ugcs.gprvisualizer.app.scripts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ScriptMetadataLoader {
	/**
	 * Loads scripts from the specified directory.
	 *
	 * @param scriptsDir the directory to load scripts from
	 * @return a list of metadata for the scripts found in the directory
	 * @throws IOException if an I/O error occurs while reading the directory or files
	 */
	List<ScriptMetadata> loadScriptMetadata(Path scriptsDir) throws IOException;
}

package com.ugcs.geohammer.service.script;

import java.io.IOException;
import java.util.List;

public interface ScriptMetadataLoader {
	/**
	 * Loads scripts from the application and user scripts directories.
	 * A user script replaces the application script with the same file name.
	 *
	 * @return a list of metadata for the scripts found in the directories,
	 * application scripts first
	 * @throws IOException if an I/O error occurs while reading a directory
	 */
	List<ScriptMetadata> loadScriptMetadata() throws IOException;
}

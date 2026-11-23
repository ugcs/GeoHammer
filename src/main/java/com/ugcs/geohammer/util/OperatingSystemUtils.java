package com.ugcs.geohammer.util;

import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Utility class for operating system detection and OS-specific operations.
 * Thread-safe and optimized with cached values.
 */
public final class OperatingSystemUtils {

	private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

	private static final boolean IS_WINDOWS = OS_NAME.contains("win");
	private static final boolean IS_MAC = OS_NAME.contains("mac");
	private static final boolean IS_LINUX = OS_NAME.contains("nux") || OS_NAME.contains("nix");
	private static final boolean IS_UNIX = IS_LINUX || IS_MAC || OS_NAME.contains("aix") || OS_NAME.contains("sunos");

	private OperatingSystemUtils() {
		// Utility class, prevent instantiation
	}

	/**
	 * Checks if the current operating system is Windows.
	 *
	 * @return true if running on Windows, false otherwise
	 */
	@SuppressWarnings("unused")
	public static boolean isWindows() {
		return IS_WINDOWS;
	}

	/**
	 * Checks if the current operating system is macOS.
	 *
	 * @return true if running on macOS, false otherwise
	 */
	@SuppressWarnings("unused")
	public static boolean isMac() {
		return IS_MAC;
	}

	/**
	 * Checks if the current operating system is Linux.
	 *
	 * @return true if running on Linux, false otherwise
	 */
	@SuppressWarnings("unused")
	public static boolean isLinux() {
		return IS_LINUX;
	}

	/**
	 * Checks if the current operating system is Unix-like (Linux, macOS, AIX, Solaris).
	 *
	 * @return true if running on Unix-like system, false otherwise
	 */
	@SuppressWarnings("unused")
	public static boolean isUnix() {
		return IS_UNIX;
	}

	/**
	 * Gets the operating system name.
	 *
	 * @return the OS name (e.g., "Windows 10", "Linux", "Mac OS X")
	 */
	@SuppressWarnings("unused")
	public static String getOsName() {
		return OS_NAME;
	}

	// run through a shell: resolves on a terminal-like PATH
	public static List<String> toShellCommand(String command) {
		if (isWindows()) {
			return List.of("cmd", "/c", command);
		}
		String shell = System.getenv("SHELL");
		if (Strings.isNullOrEmpty(shell)) {
			shell = "/bin/sh";
		}
		return List.of(shell, "-lc", command);
	}

	// first executable with the name in the directories, in the given order
	public static @Nullable Path findExecutable(String name, List<Path> directories) {
		if (Strings.isNullOrEmpty(name)) {
			return null;
		}
		// a name with an extension is used as is
		List<String> fileNames = isWindows() && !name.contains(".")
				? List.of(name + ".exe", name + ".cmd")
				: List.of(name);
		for (Path directory : directories) {
			for (String fileName : fileNames) {
				Path path = directory.resolve(fileName);
				if (Files.isRegularFile(path) && Files.isExecutable(path)) {
					return path;
				}
			}
		}
		return null;
	}

	// absolute path of the executable, as the shell resolves it
	public static @Nullable Path resolveExecutable(String name) throws IOException {
		if (Strings.isNullOrEmpty(name)) {
			return null;
		}
		String command = (isWindows() ? "where " : "which ") + name;
		Process process = new ProcessBuilder(toShellCommand(command))
				.redirectErrorStream(true)
				.start();
		try (BufferedReader reader = process.inputReader()) {
			String line;
			while ((line = reader.readLine()) != null) {
				Path path = toExecutablePath(line);
				if (path != null) {
					return path;
				}
			}
			return null;
		} finally {
			process.destroyForcibly();
		}
	}

	private static @Nullable Path toExecutablePath(String line) {
		Path path;
		try {
			path = Path.of(line.trim());
		} catch (InvalidPathException e) {
			return null;
		}
		if (!path.isAbsolute() || !Files.isRegularFile(path) || !Files.isExecutable(path)) {
			return null;
		}
		return path;
	}
}

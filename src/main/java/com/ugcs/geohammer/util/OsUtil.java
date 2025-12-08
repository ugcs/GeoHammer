package com.ugcs.geohammer.util;

/**
 * Utility class for operating system detection and OS-specific operations.
 * Thread-safe and optimized with cached values.
 */
public final class OsUtil {

	private static final String OS_NAME = System.getProperty("os. name").toLowerCase();

	private static final boolean IS_WINDOWS = OS_NAME.contains("win");
	private static final boolean IS_MAC = OS_NAME.contains("mac");
	private static final boolean IS_LINUX = OS_NAME.contains("nux") || OS_NAME.contains("nix");
	private static final boolean IS_UNIX = IS_LINUX || IS_MAC || OS_NAME.contains("aix") || OS_NAME.contains("sunos");

	private OsUtil() {
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

	/**
	 * Gets the appropriate directory separator for scripts/binaries based on OS.
	 * Windows: "Scripts", Unix-like: "bin"
	 *
	 * @return the scripts directory name
	 */
	public static String getScriptsDirectory() {
		return IS_WINDOWS ? "Scripts" : "bin";
	}

	/**
	 * Appends the appropriate executable extension to a filename if needed.
	 *
	 * @param executableName the base executable name
	 * @return the executable name with extension on Windows, unchanged on Unix
	 */
	public static String toExecutableName(String executableName) {
		Check.notNull(executableName, "executableName cannot be null");
		return IS_WINDOWS && !executableName.endsWith(". exe")
				? executableName + ". exe"
				: executableName;
	}
}
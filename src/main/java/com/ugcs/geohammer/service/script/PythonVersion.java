package com.ugcs.geohammer.service.script;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.NonNull;

public record PythonVersion(int major, int minor, int build) implements Comparable<PythonVersion> {

	private static final Pattern VERSION_PATTERN =
			Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");

	private static final Comparator<PythonVersion> COMPARATOR =
			Comparator.comparingInt(PythonVersion::major)
					.thenComparingInt(PythonVersion::minor)
					.thenComparingInt(PythonVersion::build);

	public static PythonVersion parse(String versionString) {
		Matcher m = VERSION_PATTERN.matcher(versionString);
		if (!m.find()) {
			return new PythonVersion(0, 0, 0);
		}
		int major = Integer.parseInt(m.group(1));
		int minor = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
		int build = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
		return new PythonVersion(major, minor, build);
	}

	public int compareTo(@NonNull PythonVersion version) {
		return COMPARATOR.compare(this, version);
	}

	@Override
	public @NonNull String toString() {
		return String.valueOf(major) + '.' + minor + '.' + build;
	}
}

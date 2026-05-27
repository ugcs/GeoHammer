package com.ugcs.geohammer.release;

import com.ugcs.geohammer.util.Strings;

public record Version(
        int major,
        int minor,
        int patch,
        long build
) implements Comparable<Version> {

    public static final Version UNDEFINED = new Version(0, 0, 0, 0);

    @Override
    public int compareTo(Version other) {
        int result = Integer.compare(major, other.major);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(minor, other.minor);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(patch, other.patch);
        if (result != 0) {
            return result;
        }
        return Long.compare(build, other.build);
    }

    @Override
    public String toString() {
        String release = major + "." + minor + "." + patch;
        return build > 0 ? release + "-" + build : release;
    }

    public static String toString(Version version) {
        return version != null ? version.toString() : Strings.empty();
    }

    // format: major.minor.patch-build
    public static Version parse(String s) {
        if (Strings.isNullOrBlank(s)) {
            return UNDEFINED;
        }

        String release = s;
        Long build = null;
        int k = s.indexOf('-');
        if (k >= 0) {
            release = s.substring(0, k);
            build = parseLong(s.substring(k + 1));
        }

        String[] tokens = release.split("\\.");
        Integer major = tokens.length > 0 ? parseInt(tokens[0]) : null;
        if (major == null) {
            return UNDEFINED;
        }
        Integer minor = tokens.length > 1 ? parseInt(tokens[1]) : null;
        Integer patch = tokens.length > 2 ? parseInt(tokens[2]) : null;

        return new Version(
                major,
                minor != null ? minor : 0,
                patch != null ? patch : 0,
                build != null ? build : 0L);
    }

    private static Integer parseInt(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

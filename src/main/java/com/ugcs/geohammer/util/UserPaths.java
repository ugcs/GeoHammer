package com.ugcs.geohammer.util;

import java.nio.file.Path;

public final class UserPaths {

    public static final Path HOME = Path.of(System.getProperty("user.home"));

    public static final Path GEOHAMMER_PATH = HOME.resolve(".geohammer");

    private UserPaths() {
    }
}

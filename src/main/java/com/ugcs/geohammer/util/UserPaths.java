package com.ugcs.geohammer.util;

import java.nio.file.Path;

public final class UserPaths {

    public static final String HOME = System.getProperty("user.home");

    public static final Path GEOHAMMER_PATH = Path.of(HOME, ".geohammer");

    private UserPaths() {
    }
}

package com.ugcs.geohammer.util;

import java.nio.file.Path;

public final class UserDataFolder {

    private static final String FOLDER_NAME = ".geohammer";

    private UserDataFolder() {
    }

    public static Path getPath() {
        return Path.of(System.getProperty("user.home"), FOLDER_NAME);
    }
}

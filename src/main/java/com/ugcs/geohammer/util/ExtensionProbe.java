package com.ugcs.geohammer.util;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ExtensionProbe implements FileProbe {

    private final Set<String> extensions;

    public ExtensionProbe(String... extensions) {
        this.extensions = normalize(extensions);
    }

    private static Set<String> normalize(String... extensions) {
        if (extensions == null || extensions.length == 0) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>(extensions.length);
        for (String extension : extensions) {
            extension = Strings.nullToEmpty(extension)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            if (!extension.isEmpty()) {
                normalized.add(extension);
            }
        }
        return normalized;
    }

    @Override
    public boolean matches(File file) {
        if (file == null) {
            return false;
        }
        String extension = Strings.nullToEmpty(FileNames.getExtension(file.getName()))
                .toLowerCase(Locale.ROOT);
        return extensions.contains(extension);
    }
}

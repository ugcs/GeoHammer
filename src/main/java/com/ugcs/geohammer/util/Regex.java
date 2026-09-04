package com.ugcs.geohammer.util;

import org.jspecify.annotations.Nullable;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

public final class Regex {

    private Regex() {
    }

    public static String quote(String s) {
        return Pattern.quote(s);
    }

    public static boolean isValid(String regex) {
        return compile(regex) != null;
    }

    public static @Nullable Pattern compileMultiline(@Nullable String regex) {
        return compile(regex, Pattern.MULTILINE | Pattern.DOTALL);
    }

    public static @Nullable Pattern compile(@Nullable String regex) {
        return compile(regex, 0);
    }

    private static @Nullable Pattern compile(@Nullable String regex, int flags) {
        if (Strings.isNullOrBlank(regex)) {
            return null;
        }
        try {
            return Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }
}

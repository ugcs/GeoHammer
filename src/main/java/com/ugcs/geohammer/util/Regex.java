package com.ugcs.geohammer.util;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class Regex {

    private Regex() {
    }

    public static boolean isValid(String regex) {
        if (Strings.isNullOrBlank(regex)) {
            return false;
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return false;
        }
        return true;
    }

    public static @Nullable Pattern compile(@Nullable String regex) {
        if (Strings.isNullOrBlank(regex)) {
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    public static Pattern splitPattern(String separator, boolean repeatable) {
        Check.notNull(separator);
        String regex = Pattern.quote(separator);
        if (repeatable) {
            regex += "+";
        }
        return Pattern.compile(regex);
    }
}

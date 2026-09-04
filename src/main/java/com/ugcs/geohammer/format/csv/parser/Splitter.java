package com.ugcs.geohammer.format.csv.parser;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Strings;
import com.ugcs.geohammer.util.Text;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record Splitter(String separator, boolean repeatable) {

    public Splitter {
        Check.notEmpty(separator);
    }

    public static Splitter ofEscaped(String separator, boolean repeatable) {
        Check.notEmpty(separator);
        return new Splitter(Text.unescape(separator), repeatable);
    }

    public static List<Splitter> ofEscaped(@Nullable Collection<String> separators, boolean repeatable) {
        if (Nulls.isNullOrEmpty(separators)) {
            return List.of();
        }
        List<Splitter> splitters = new ArrayList<>(separators.size());
        for (String separator : separators) {
            splitters.add(ofEscaped(separator, repeatable));
        }
        return splitters;
    }

    public int numTokens(@Nullable String line) {
        if (Strings.isNullOrEmpty(line)) {
            return 0;
        }

        int n = separator.length();
        int i = line.indexOf(separator);
        if (i == -1) {
            return 1;
        }

        int numTokens = 0;
        int from;
        while (i != -1) {
            numTokens++;
            from = i + n;
            if (repeatable) {
                while (line.startsWith(separator, from)) {
                    from += n;
                }
            }
            i = line.indexOf(separator, from);
        }
        numTokens++;
        return numTokens;
    }

    public List<String> split(@Nullable String line) {
        if (Strings.isNullOrEmpty(line)) {
            return List.of();
        }

        int n = separator.length();
        int i = line.indexOf(separator);
        if (i == -1) {
            return List.of(line.trim());
        }

        List<String> tokens = new ArrayList<>();
        int from = 0;
        while (i != -1) {
            tokens.add(line.substring(from, i).trim());
            from = i + n;
            if (repeatable) {
                while (line.startsWith(separator, from)) {
                    from += n;
                }
            }
            i = line.indexOf(separator, from);
        }
        tokens.add(line.substring(from).trim());
        return tokens;
    }

    public String[] splitToArray(@Nullable String line) {
        return split(line).toArray(new String[0]);
    }

    public static @Nullable Splitter best(Iterable<Splitter> splitters, @Nullable String line) {
        Splitter max = null;
        int maxTokens = 0;

        for (Splitter splitter : splitters) {
            if (splitter == null) {
                continue;
            }
            int numTokens = splitter.numTokens(line);
            if (numTokens > maxTokens) {
                max = splitter;
                maxTokens = numTokens;
            }
        }
        return max;
    }
}

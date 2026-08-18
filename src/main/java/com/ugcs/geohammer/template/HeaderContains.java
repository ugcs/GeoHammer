package com.ugcs.geohammer.template;

import com.ugcs.geohammer.util.Strings;

import java.util.ArrayList;
import java.util.List;

public class HeaderContains implements ColumnMatcher {

    private final List<String> parts;

    public HeaderContains(String... parts) {
        this.parts = new ArrayList<>(parts.length);
        for (String part : parts) {
            part = Strings.toLowerCase(part);
            if (!Strings.isNullOrEmpty(part)) {
                this.parts.add(part);
            }
        }
    }

    @Override
    public boolean matches(String header) {
        if (Strings.isNullOrEmpty(header)) {
            return false;
        }

        header = Strings.toLowerCase(header);
        for (String part : parts) {
            if (header.contains(part)) {
                return true;
            }
        }
        return false;
    }
}

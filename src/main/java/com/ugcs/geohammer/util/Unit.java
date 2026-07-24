package com.ugcs.geohammer.util;

import java.util.function.Function;

public class Unit {

    private final String singular;

    private final String plural;

    private final String separator;

    public Unit(String singular, String plural) {
        this(singular, plural, " ");
    }

    public Unit(String singular, String plural, String separator) {
        this.singular = Strings.emptyToNull(singular);
        this.plural = Strings.emptyToNull(plural);
        this.separator = Strings.emptyToNull(separator);
    }

    public String format(Number value) {
        return format(value, Object::toString);
    }

    public String format(Number value, Function<Number, String> formatter) {
        if (value == null) {
            return Strings.empty();
        }
        StringBuilder sb = new StringBuilder(formatter.apply(value));
        if (singular != null) {
            if (separator != null) {
                sb.append(separator);
            }
            String unit = singular;
            if (plural != null && Math.abs(value.doubleValue()) != 1.0) {
                unit = plural;
            }
            sb.append(unit);
        }
        return sb.toString();
    }

    public static String defaultPlural(String singular) {
        return singular + "s";
    }

    public static Unit empty() {
        return new Unit(null, null);
    }

    public static Unit word(String singular) {
        Check.notEmpty(singular);
        return new Unit(singular, defaultPlural(singular));
    }

    public static Unit word(String singular, String plural) {
        Check.notEmpty(singular);
        Check.notEmpty(plural);
        return new Unit(singular, plural);
    }

    public static Unit word(String singular, String plural, String separator) {
        Check.notEmpty(singular);
        Check.notEmpty(plural);
        return new Unit(singular, plural, separator);
    }

    public static Unit symbol(String symbol) {
        Check.notEmpty(symbol);
        return new Unit(symbol, null);
    }

    public static Unit symbol(String symbol, String separator) {
        Check.notEmpty(symbol);
        return new Unit(symbol, null, separator);
    }
}

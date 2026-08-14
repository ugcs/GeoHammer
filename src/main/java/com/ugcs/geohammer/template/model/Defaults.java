package com.ugcs.geohammer.template.model;

import com.ugcs.geohammer.util.Text;

import java.util.List;

public final class Defaults {

    public static final String COMMENT_PREFIX = "#";

    public static final List<String> MATCH_SEPARATORS = List.of(
            ",",
            ";",
            "\\t",
            " ",
            "|");

    public static final String SEPARATOR = ",";

    public static final String INDEX_HEADER_PREFIX = "column_";

    public static final List<String> DATE_TIME_FORMATS = List.of(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.f+",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.f+",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss.f+",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss.f+",
            "dd.MM.yyyy HH:mm:ss",
            "dd.MM.yyyy HH:mm:ss.f+",
            Text.GPST_FORMAT);

    public static final List<String> DATE_FORMATS = List.of(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "MM/dd/yyyy",
            "MM/dd/yy",
            "dd/MM/yyyy",
            "dd/MM/yy",
            "dd.MM.yyyy",
            "dd.MM.yy",
            "dd-MM-yyyy",
            "dd-MM-yy",
            "d-MMM-yyyy",
            "d-MMM-yy",
            "yyyyMMdd");

    public static final List<String> TIME_FORMATS = List.of(
            "HH:mm:ss",
            "HH:mm:ss.f+",
            "H:mm:ss",
            "H:mm:ss.f+",
            "HHmmss.f+",
            "HH:mm");

    public static final List<String> UNITS = List.of(
            "m",
            "cm",
            "nT",
            "°",
            "s",
            "m/s",
            "ppm",
            "%",
            "C",
            "Bq/kg",
            "Bar");

    private Defaults() {
    }
}

package com.ugcs.geohammer.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class GsonConfig {

    public static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .setPrettyPrinting()
            .create();

    private GsonConfig() {
    }
}

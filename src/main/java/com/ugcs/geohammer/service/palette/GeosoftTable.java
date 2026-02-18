package com.ugcs.geohammer.service.palette;

import com.ugcs.geohammer.util.Resources;
import com.ugcs.geohammer.util.Strings;
import org.jspecify.annotations.NonNull;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public final class GeosoftTable {

    private GeosoftTable() {
    }

    public static BandGradient loadSpectrum(@NonNull String resourcePath) {
        try (InputStream in = Resources.get(resourcePath)) {
            Color[] colors = GeosoftTable.parseColorMap(in);
            return new BandGradient(colors);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Color[] parseColorMap(@NonNull InputStream in) throws IOException {
        List<Color> colors = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) {
                Color color = parseColor(line);
                if (color != null) {
                    colors.add(color);
                }
            }
        }
        return colors.toArray(new Color[0]);
    }

    private static Color parseColor(String line) {
        line = Strings.trim(line);
        if (Strings.isNullOrEmpty(line)) {
            return null;
        }
        if (line.startsWith("{")) {
            return null; // header
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) {
            return null;
        }
        return new Color(
                parseChannel(tokens[0]),
                parseChannel(tokens[1]),
                parseChannel(tokens[2])
        );
    }

    private static int parseChannel(String s) {
        int v = (int)Math.round(Double.parseDouble(s));
        return Math.clamp(v, 0, 255);
    }
}

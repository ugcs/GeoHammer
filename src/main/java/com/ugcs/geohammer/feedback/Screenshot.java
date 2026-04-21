package com.ugcs.geohammer.feedback;

import com.ugcs.geohammer.util.Check;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class Screenshot {

    private static final String DEFAULT_FORMAT = "png";

    private final byte[] bytes;

    private final String fileName;

    public Screenshot(byte[] bytes, String fileName) {
        Check.notNull(bytes);
        Check.notEmpty(fileName);

        this.bytes = bytes;
        this.fileName = fileName;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getFileName() {
        return fileName;
    }

    public static Screenshot take(Scene scene, String fileName, String format) {
        Check.notNull(scene);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            WritableImage image = scene.snapshot(null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), format, out);
            return new Screenshot(out.toByteArray(), fileName);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Screenshot take(Scene scene, String fileName) {
        return take(scene, fileName, DEFAULT_FORMAT);
    }
}

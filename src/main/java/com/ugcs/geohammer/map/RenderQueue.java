package com.ugcs.geohammer.map;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import javafx.geometry.Point2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RenderQueue {

    private static final Logger log = LoggerFactory.getLogger(RenderQueue.class);

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());

    private final Model model;

    private BufferedImage renderImage;

    private Dimension renderSize = new Dimension(512, 512);

    private final AtomicReference<Frame> lastFrame = new AtomicReference<>();

    public RenderQueue(Model model) {
        this.model = model;
    }

    public void setRenderSize(Dimension size) {
        this.renderSize = size;
    }

    public Frame getLastFrame() {
        return lastFrame.get();
    }

    public void clear() {
        lastFrame.set(null);
    }

    public void submit() {
        executor.submit(createRenderTask());
    }

    private Runnable createRenderTask() {
        return () -> {
            try {
                if (!executor.getQueue().isEmpty()) {
                    return;
                }

                MapField field = new MapField(model.getMapField());

                actualizeRenderImage();
                draw(renderImage, field);

                if (renderImage == null) {
                    lastFrame.set(null);
                } else {
                    BufferedImage frameImage = copyImage(renderImage);
                    lastFrame.set(new Frame(frameImage, field));
                    onReady();
                }
            } catch (Exception e) {
                log.error("Error", e);
            }
        };
    }

    public abstract void draw(BufferedImage image, MapField field);

    public void onReady() {
        log.debug("Ready");
    }

    protected void setRenderImage(BufferedImage renderImage) {
        this.renderImage = renderImage;
    }

    protected void actualizeRenderImage() {
        if (renderImage == null
                || renderImage.getWidth() != renderSize.width
                || renderImage.getHeight() != renderSize.height) {
            renderImage = new BufferedImage(
                    Math.max(1, renderSize.width),
                    Math.max(1, renderSize.height),
                    BufferedImage.TYPE_INT_ARGB
            );
        }

        clearImage(renderImage);
    }

    private void clearImage(BufferedImage image) {
        if (image == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D)image.getGraphics();

        AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f);
        g2.setComposite(composite);
        g2.setColor(new Color(0, 0, 0, 0));
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.dispose();
    }

    private BufferedImage copyImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        BufferedImage copy = new BufferedImage(
                image.getWidth(),
                image.getHeight(),
                image.getType()
        );
        Graphics2D g = copy.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    public void drawWithTransform(Graphics2D g2, MapField field, Frame frame) {
        if (frame == null) {
            return;
        }

        LatLon frameCenter = frame.field().getSceneCenter();
        Point2D offset = frameCenter != null
                ? field.latLonToScreen(frameCenter)
                : new Point2D(0, 0);

        double scale = Math.pow(2, field.getZoom() - frame.field().getZoom());
        BufferedImage image = frame.image();
        double width = image.getWidth() * scale;
        double height = image.getHeight() * scale;

        g2.drawImage(image,
                (int) (offset.getX() - 0.5 * width),
                (int) (offset.getY() - 0.5 * height),
                (int) width,
                (int) height,
                null);
    }

    public record Frame(BufferedImage image, MapField field) {

        public Frame {
            Check.notNull(image);
            Check.notNull(field);
        }
    }
}

package com.ugcs.geohammer.map;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.ugcs.geohammer.model.LatLon;
import com.ugcs.geohammer.model.MapField;
import com.ugcs.geohammer.model.Model;
import javafx.geometry.Point2D;

public abstract class ThrQueue {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    Model model;
    BufferedImage frontImg;
    BufferedImage backImg;
    Dimension backImgSize
            = new Dimension(512, 512);
    ThrFront actual;

    public ThrQueue(Model model) {
        this.model = model;
    }

    public ThrFront getFront() {
        return actual;
    }

    public void add() {
        executor.submit(getWorker());
    }

    private Runnable getWorker() {
        return () -> {
            try {
                if (executor.getQueue().size() > 0) {
                    return;
                }

                actualizeBackImg();

                MapField field = new MapField(model.getMapField());

                ///
                draw(backImg, field);
                ///

                BufferedImage img = backImg;
                backImg = frontImg;
                frontImg = img;

                if (img == null) {
                    backImg = null;
                    frontImg = null;

                    clear();
                    return;
                }
                actual = new ThrFront(img, field);
                ready();


            } catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    protected abstract void draw(BufferedImage backImg, MapField field); //{

    public void ready() {
        System.out.println("ready");
    }

    public void setBackImgSize(Dimension size) {
        this.backImgSize = size;
    }

    public void setBackImg(BufferedImage backImg) {
        this.backImg = backImg;
    }

    protected void actualizeBackImg() {
        if (backImg != null
                && backImg.getWidth() == backImgSize.width
                && backImg.getHeight() == backImgSize.height) {

        } else {
            backImg = new BufferedImage(Math.max(1, backImgSize.width), Math.max(1, backImgSize.height), BufferedImage.TYPE_INT_ARGB);
        }

        if (backImg != null) {
            Graphics2D g2 = (Graphics2D) backImg.getGraphics();

            AlphaComposite composite = AlphaComposite.getInstance(AlphaComposite.CLEAR, 0.0f);
            g2.setComposite(composite);
            g2.setColor(new Color(0, 0, 0, 0));

            g2.fillRect(0, 0, backImg.getWidth(), backImg.getHeight());

            g2.dispose();
        }
    }

    public void clear() {
        actual = null;
    }

    public void drawImgOnChangedField(Graphics2D g2, MapField currentField, ThrFront front) {
        if (front == null) {
            return;
        }

        LatLon frontCenter = front.getField().getSceneCenter();
        Point2D offset = frontCenter != null
                ? currentField.latLonToScreen(frontCenter)
                : new Point2D(0, 0);

        double scale = Math.pow(2, currentField.getZoom() - front.getField().getZoom());
        BufferedImage img = front.getImg();
        double imgWidth = img.getWidth() * scale;
        double imgHeight = img.getHeight() * scale;

        g2.drawImage(img,
                (int) (offset.getX() - 0.5 * imgWidth),
                (int) (offset.getY() - 0.5 * imgHeight),
                (int) imgWidth,
                (int) imgHeight,
                null);
    }
}

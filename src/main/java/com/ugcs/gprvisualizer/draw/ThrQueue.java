package com.ugcs.gprvisualizer.draw;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.github.thecoldwine.sigrun.common.ext.MapField;
import com.ugcs.gprvisualizer.app.MapView;
import com.ugcs.gprvisualizer.gpr.Model;
import javafx.geometry.Point2D;

public abstract class ThrQueue {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
    private final Dimension windowSize;
    Model model;
    BufferedImage frontImg;
    BufferedImage backImg;
    ThrFront actual;

    public ThrQueue(Model model, MapView mapView) {
        this.model = model;
        this.windowSize = mapView.getWndSize();
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

    protected void actualizeBackImg() {
        if (backImg != null
                && backImg.getWidth() == windowSize.width
                && backImg.getHeight() == windowSize.height) {

        } else {
            backImg = new BufferedImage(Math.max(1, windowSize.width), Math.max(1, windowSize.height), BufferedImage.TYPE_INT_ARGB);
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

        Point2D offst = front.getField().getSceneCenter() != null ? currentField.latLonToScreen(front.getField().getSceneCenter()) : new Point2D(0, 0);

        double scale = Math.pow(2, currentField.getZoom() - front.getField().getZoom());
        BufferedImage tmpImg = front.getImg();
        g2.drawImage(tmpImg,
                (int) (offst.getX() - tmpImg.getWidth() / 2 * scale),
                (int) (offst.getY() - tmpImg.getHeight() / 2 * scale),
                (int) (tmpImg.getWidth() * scale),
                (int) (tmpImg.getHeight() * scale),
                null);
    }
}

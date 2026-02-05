package com.ugcs.geohammer.view;

import javafx.animation.AnimationTimer;

import java.util.concurrent.atomic.AtomicBoolean;

public class PaintLimiter {

    private final long framePeriod;

    private long lastPulse;

    private long accumulated;

    private final Runnable paint;

    private final AtomicBoolean paintRequested = new AtomicBoolean(false);

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (!paintRequested.getAndSet(false)) {
                return;
            }

            accumulated += now - lastPulse;
            lastPulse = now;

            if (accumulated >= framePeriod) {
                accumulated %= framePeriod;
                paint.run();
            }
        }
    };

    public PaintLimiter(int fps, Runnable paint) {
        this.framePeriod = 1_000_000_000L / fps;
        this.paint = paint;
        timer.start();
    }

    public void requestPaint() {
        paintRequested.set(true);
    }

    public void stop() {
        timer.stop();
    }
}

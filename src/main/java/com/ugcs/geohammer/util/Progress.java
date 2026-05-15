package com.ugcs.geohammer.util;

import java.util.function.DoubleConsumer;

public class Progress {

    private final Progress parent;

    private final DoubleConsumer onChange;

    // max ticks to complete the task
    private int maxTicks = 1;

    // num completed ticks
    private int ticks = 0;

    // fraction of the tick in progress
    private double tickFraction = 0;

    public Progress(DoubleConsumer onChange) {
        this.parent = null;
        this.onChange = onChange;
    }

    private Progress(Progress parent) {
        this.parent = Check.notNull(parent);
        this.onChange = null;
    }

    public void setMaxTicks(int maxTicks) {
        Check.condition(maxTicks >= Math.max(ticks, 1));
        this.maxTicks = maxTicks;
        notifyChange();
    }

    public void setTicks(int ticks) {
        this.ticks = Math.clamp(ticks, 0, maxTicks);
        tickFraction = 0.0;
        notifyChange();
    }

    public void tick() {
        setTicks(ticks + 1);
    }

    public void reset() {
        setTicks(0);
    }

    public void complete() {
        setTicks(maxTicks);
    }

    public double fraction() {
        return Math.min(1.0, (ticks + tickFraction) / maxTicks);
    }

    public Progress tickProgress() {
        return new Progress(this);
    }

    private void notifyChange() {
        Progress progress = this;
        while (progress.parent != null) {
            progress.parent.tickFraction = progress.fraction();
            progress = progress.parent;
        }
        if (progress.onChange != null) {
            progress.onChange.accept(progress.fraction());
        }
    }
}

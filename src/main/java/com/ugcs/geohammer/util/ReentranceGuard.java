package com.ugcs.geohammer.util;

// runs an action with a latch held,
// skipping the action when the latch is already held
public final class ReentranceGuard {

    private volatile boolean latch;

    public boolean isLatched() {
        return latch;
    }

    public void run(Runnable action) {
        if (latch) {
            return;
        }
        latch = true;
        try {
            action.run();
        } finally {
            latch = false;
        }
    }
}

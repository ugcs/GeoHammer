package com.ugcs.geohammer.model.event;

import com.ugcs.geohammer.model.IndexRange;

public class DepthRangeUpdatedEvent extends BaseEvent {

    private final IndexRange depthRange;

    public DepthRangeUpdatedEvent(Object source, IndexRange depthRange) {
        super(source);
        this.depthRange = depthRange;
    }

    public IndexRange getDepthRange() {
        return depthRange;
    }
}

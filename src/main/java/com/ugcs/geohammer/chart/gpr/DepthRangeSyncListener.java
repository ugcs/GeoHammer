package com.ugcs.geohammer.chart.gpr;

import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.DepthRangeUpdatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DepthRangeSyncListener {

    @Autowired
    private Model model;

    @EventListener
    public void onDepthRangeUpdated(DepthRangeUpdatedEvent event) {
        model.getGprCharts().stream()
                .filter(chart -> chart != event.getSource())
                .forEach(chart -> chart.applyDepthRange(event.getDepthRange()));
    }
}

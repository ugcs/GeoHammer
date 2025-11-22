package com.ugcs.geohammer.model.event;

public class GriddingParamsSetted extends BaseEvent {

    private static final double MIN_CELL_SIZE = 0.01;

    private final double cellSize;
    private final double blankingDistance;
    private final boolean toAll;

    private final boolean analyticSignalEnabled;
    private final boolean hillShadingEnabled;
    private final boolean smoothingEnabled;

    public GriddingParamsSetted(
            Object source,
            double cellSize,
            double blankingDistance,
            boolean toAll,
            boolean analyticSignalEnabled,
            boolean hillShadingEnabled,
            boolean smoothingEnabled) {
        super(source);

        this.cellSize = Math.max(cellSize, MIN_CELL_SIZE);
        this.blankingDistance = blankingDistance;
        this.toAll = toAll;

        this.analyticSignalEnabled = analyticSignalEnabled;
        this.hillShadingEnabled = hillShadingEnabled;
        this.smoothingEnabled = smoothingEnabled;
    }

    public double getCellSize() {
        return cellSize;
    }

    public double getBlankingDistance() {
        return blankingDistance;
    }

    public boolean isToAll() {
        return toAll;
    }

    public boolean isAnalyticSignalEnabled() {
        return analyticSignalEnabled;
    }

    public boolean isHillShadingEnabled() {
        return hillShadingEnabled;
    }

    public boolean isSmoothingEnabled() {
        return smoothingEnabled;
    }
}
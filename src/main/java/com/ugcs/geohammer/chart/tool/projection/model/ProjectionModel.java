package com.ugcs.geohammer.chart.tool.projection.model;

import org.springframework.stereotype.Component;

@Component
public class ProjectionModel {

    private final Viewport viewport;

    private final TraceSelection selection;

    private final ProjectionOptions projectionOptions;

    private final GridOptions gridOptions;

    private final RenderOptions renderOptions;

    private final ProjectionResult result;

    public ProjectionModel(
            Viewport viewport,
            TraceSelection selection,
            ProjectionOptions projectionOptions,
            GridOptions gridOptions,
            RenderOptions renderOptions,
            ProjectionResult result) {
        this.viewport = viewport;
        this.selection = selection;
        this.projectionOptions = projectionOptions;
        this.gridOptions = gridOptions;
        this.renderOptions = renderOptions;
        this.result = result;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public TraceSelection getSelection() {
        return selection;
    }

    public ProjectionOptions getProjectionOptions() {
        return projectionOptions;
    }

    public GridOptions getGridOptions() {
        return gridOptions;
    }

    public RenderOptions getRenderOptions() {
        return renderOptions;
    }

    public ProjectionResult getResult() {
        return result;
    }
}

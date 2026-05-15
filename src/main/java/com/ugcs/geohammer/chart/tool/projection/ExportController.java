package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.model.ExportOptions;
import com.ugcs.geohammer.chart.tool.projection.model.ExportScope;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.util.Progress;
import com.ugcs.geohammer.util.Tasks;
import javafx.application.Platform;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
public class ExportController {

    private final ProjectionModel projectionModel;

    private final ExportService exportService;

    private final ExecutorService executor;

    private CompletableFuture<Void> exportTask;

    private final Object exportTaskLock = new Object();

    public ExportController(
            ProjectionModel projectionModel,
            ExportService exportService,
            ExecutorService executor) {
        this.projectionModel = projectionModel;
        this.exportService = exportService;
        this.executor = executor;
    }

    public CompletableFuture<Void> exportGrid(Path path, ExportScope scope) {
        if (path == null || scope == null) {
            return null;
        }

        synchronized (exportTaskLock) {
            if (exportTask != null && !exportTask.isDone()) {
                return exportTask;
            }

            setExporting(true);
            return exportTask = Tasks.submitCompletable(executor, () -> {
                try {
                    exportService.exportGrid(path, scope, new Progress(this::setProgress));
                    return null;
                } finally {
                    setExporting(false);
                }
            });
        }
    }

    public void cancelExport() {
        CompletableFuture<Void> task;
        synchronized (exportTaskLock) {
            task = exportTask;
        }
        if (task != null) {
            task.cancel(true);
        }
    }

    private void setProgress(double progress) {
        ExportOptions exportOptions = projectionModel.getExportOptions();
        Platform.runLater(() -> exportOptions.progressProperty().set(progress));
    }

    private void setExporting(boolean exporting) {
        ExportOptions exportOptions = projectionModel.getExportOptions();
        Platform.runLater(() -> exportOptions.exportingProperty().set(exporting));
    }
}

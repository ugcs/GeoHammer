package com.ugcs.geohammer.chart.tool.projection;

import com.ugcs.geohammer.chart.tool.projection.math.DbGain;
import com.ugcs.geohammer.chart.tool.projection.math.GainFunction;
import com.ugcs.geohammer.chart.tool.projection.model.ExportFormat;
import com.ugcs.geohammer.chart.tool.projection.model.ExportScope;
import com.ugcs.geohammer.chart.tool.projection.model.Grid;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionModel;
import com.ugcs.geohammer.chart.tool.projection.model.ProjectionResult;
import com.ugcs.geohammer.chart.tool.projection.model.RenderOptions;
import com.ugcs.geohammer.chart.tool.projection.model.TraceProfile;
import com.ugcs.geohammer.chart.tool.projection.model.TraceRay;
import com.ugcs.geohammer.chart.tool.projection.model.TraceSelection;
import com.ugcs.geohammer.format.HorizontalProfile;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.point.LasWriter;
import com.ugcs.geohammer.format.point.PlyWriter;
import com.ugcs.geohammer.format.point.ScalarPoint;
import com.ugcs.geohammer.format.point.ScalarPointWriter;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Nulls;
import com.ugcs.geohammer.util.Progress;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExportService {

    private final ProjectionModel projectionModel;

    private final TraceProfileService traceProfileService;

    private final GridService gridService;

    private final Model model;

    public ExportService(
            ProjectionModel projectionModel,
            TraceProfileService traceProfileService,
            GridService gridService,
            Model model) {
        this.projectionModel = projectionModel;
        this.traceProfileService = traceProfileService;
        this.gridService = gridService;
        this.model = model;
    }

    public void exportGrid(Path path, ExportScope scope, Progress progress) throws IOException {
        Check.notNull(path);
        Check.notNull(progress);

        ExportFormat format = ExportFormat.of(path);
        if (format == null) {
            format = ExportFormat.defaultFormat();
        }
        List<ExportRange> ranges = getExportRanges(scope);
        if (ranges.isEmpty()) {
            return;
        }
        ExportContext context = getExportContext();
        progress.reset();
        Path tmp = Files.createTempFile("geohammer-export-", ".bin");
        try {
            try (ScalarPointWriter writer = new ScalarPointWriter(tmp)) {
                writePoints(writer, ranges, context, progress);
            }
            switch (format) {
                case LAS -> LasWriter.writeScalarPoints(path, tmp);
                case PLY -> PlyWriter.writeScalarPoints(path, tmp, "amplitude");
            }
        } finally {
            Files.deleteIfExists(tmp);
            progress.complete();
        }
    }

    private List<ExportRange> getExportRanges(ExportScope scope) {
        if (scope == null) {
            return List.of();
        }

        TraceSelection selection = projectionModel.getSelection();
        List<ExportRange> ranges = new ArrayList<>();

        switch (scope) {
            case SELECTED_LINE -> {
                TraceFile selectedFile = selection.getFile();
                Integer selectedLine = selection.getLine();
                if (selectedFile != null && selectedLine != null) {
                    ranges.add(new ExportRange(selectedFile, selectedLine));
                }
            }
            case SELECTED_FILE -> {
                TraceFile selectedFile = selection.getFile();
                if (selectedFile != null) {
                    for (int line : selectedFile.getLineRanges().keySet()) {
                        ranges.add(new ExportRange(selectedFile, line));
                    }
                }
            }
            case ALL_FILES -> {
                for (TraceFile file : model.getFileManager().getGprFiles()) {
                    HorizontalProfile profile = file.getGroundProfile();
                    if (profile == null) {
                        continue; // skip files without ground profile
                    }
                    for (int line : file.getLineRanges().keySet()) {
                        ranges.add(new ExportRange(file, line));
                    }
                }
            }
        }

        return ranges;
    }

    private ExportContext getExportContext() {
        ProjectionResult result = projectionModel.getResult();

        TraceProfile profile = result.getProfile();
        Check.notNull(profile, "No active trace profile");

        Grid grid = result.getGrid();
        Check.notNull(grid, "No active grid");

        RenderOptions renderOptions = projectionModel.getRenderOptions();
        GainFunction gainFunction = new DbGain(0, renderOptions.getMaxGain());
        float gainMaxDepth = grid.getMaxDepth();

        return new ExportContext(gainFunction, gainMaxDepth);
    }

    private void writePoints(ScalarPointWriter writer,
            List<ExportRange> ranges, ExportContext context, Progress progress) throws IOException {
        Check.notNull(writer);

        if (ranges.isEmpty()) {
            return;
        }

        progress.setMaxTicks(ranges.size());
        for (ExportRange range : ranges) {
            TraceProfile profile = traceProfileService.buildTraceProfile(range.traceFile(), range.line());
            if (profile == null || Nulls.isNullOrEmpty(profile.getRays())) {
                progress.tick();
                continue;
            }
            Grid grid = gridService.buildGrid(profile, progress.tickProgress());
            if (grid == null) {
                progress.tick();
                continue;
            }
            writePoints(writer, profile, grid, context);
            progress.tick();
        }
    }

    private void writePoints(ScalarPointWriter writer, TraceProfile profile, Grid grid, ExportContext context)
            throws IOException {
        Check.notNull(writer);
        Check.notNull(profile);
        Check.notNull(grid);
        Check.notNull(context);

        GainFunction gainFunction = context.gainFunction();
        float gainMaxDepth = context.gainMaxDepth();

        int width = grid.getWidth();
        int height = grid.getHeight();
        for (int i = 0; i < width; i++) {
            // per-column lookup since x is
            // constant for all depths along a single trace
            Point2D columnPoint = grid.getPoint(new Grid.Index(i, 0));
            Point3D columnWorldPoint = toWorld(columnPoint, profile);
            if (columnWorldPoint == null) {
                continue;
            }

            for (int j = 0; j < height; j++) {
                Grid.Index index = new Grid.Index(i, j);
                Grid.Cell cell = grid.getCell(index);
                if (cell == null || Float.isNaN(cell.getValue())) {
                    continue;
                }
                Point2D point = grid.getPoint(index);

                float gain = gainFunction.getGain(gainMaxDepth > 0 ? cell.getDepth() / gainMaxDepth : 0);
                float value = gain * cell.getValue();
                writer.write(new ScalarPoint(
                        columnWorldPoint.getX(),
                        columnWorldPoint.getY(),
                        point.getY(),
                        value));
            }
        }
    }

    private Point3D toWorld(Point2D point, TraceProfile profile) {
        Check.notNull(profile);

        if (point == null) {
            return null;
        }
        List<TraceRay> rays = profile.getRays();
        if (Nulls.isNullOrEmpty(rays)) {
            return null;
        }
        int traceIndex = lookupTraceIndex(rays, point.getX());
        TraceRay ray = rays.get(traceIndex);
        return new Point3D(ray.world().getX(), ray.world().getY(), point.getY());
    }

    private int lookupTraceIndex(List<TraceRay> rays, double x) {
        if (rays.isEmpty()) {
            return -1;
        }
        int l = 0;
        int r = rays.size() - 1;
        while (l <= r) {
            int m = (l + r) / 2;
            double xm = rays.get(m).origin().getX();
            if (xm < x) {
                l = m + 1;
            } else if (xm > x) {
                r = m - 1;
            } else {
                return m;
            }
        }
        if (l >= rays.size()) {
            return rays.size() - 1;
        }
        if (r < 0) {
            return 0;
        }
        double dl = Math.abs(rays.get(l).origin().getX() - x);
        double dr = Math.abs(rays.get(r).origin().getX() - x);
        return dl < dr ? l : r;
    }

    private record ExportRange(TraceFile traceFile, int line) {
    }

    private record ExportContext(GainFunction gainFunction, float gainMaxDepth) {
    }
}

package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.csv.SensorLineChart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Strings;

public class ApplyFilter extends McpTool {

    public ApplyFilter(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "apply_filter";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Run a filter on a data series of an open data file. "
                + "The result is written into a new series named after the source with a filter suffix: "
                + "_LPF (lowpass), _LAG (timelag), _RM (running_median). The source series is not changed. "
                + "Filters: lowpass (value = filter length in measurements), "
                + "timelag (value = shift in measurements, may be negative), "
                + "running_median (value = window size in measurements). "
                + "The timelag shift is applied as result[i] = source[i + value]: a positive value moves "
                + "the series earlier in time (towards the file start), a negative value moves it later. "
                + "NOT UNDOABLE: this tool only adds a series, so the {{undo}} tool does not reverse it and "
                + "would instead undo the last data modification made before it. To reverse a filter, "
                + "delete the series it created with {{remove_series}}.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode filterName = addProperty(schema, "filter", "string",
                "Filter to run: lowpass, timelag or running_median.");
        filterName.putArray("enum").add("lowpass").add("timelag").add("running_median");
        addProperty(schema, "series", "string",
                "Source series name; defaults to the series selected in the UI.");
        addProperty(schema, "value", "integer", "Filter parameter, see the filter list.");
        schema.putArray("required").add("filter").add("value");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        String filter = requiredString(args, "filter");
        String seriesArg = optionalString(args, "series");
        JsonNode valueNode = args.get("value");
        if (valueNode == null || !valueNode.canConvertToInt()) {
            throw new IllegalArgumentException("value must be an integer");
        }
        int value = valueNode.asInt();

        FilterTarget target = inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            SensorLineChart chart = getSensorChart(dataFile);
            String series = !Strings.isNullOrEmpty(seriesArg)
                    ? seriesArg
                    : chart.getSelectedSeriesName();
            if (Strings.isNullOrEmpty(series)) {
                throw new IllegalArgumentException("No series selected, specify a series name");
            }
            if (!chart.getSeriesNames().contains(series)) {
                throw new IllegalArgumentException("Series has no chart: " + series
                        + "; series with charts: " + String.join(", ", chart.getSeriesNames()));
            }
            return new FilterTarget(chart, series);
        });

        // filters run on a background thread, as filter tools do
        String suffix = switch (filter) {
            case "lowpass" -> {
                if (value < 0) {
                    throw new IllegalArgumentException("Filter length must be non-negative");
                }
                target.chart().applyLowPass(target.series(), value);
                yield "_LPF";
            }
            case "timelag" -> {
                target.chart().applyTimeLag(target.series(), value);
                yield "_LAG";
            }
            case "running_median" -> {
                if (value < 1) {
                    throw new IllegalArgumentException("Window size must be positive");
                }
                target.chart().applyRunningMedian(target.series(), value);
                yield "_RM";
            }
            default -> throw new IllegalArgumentException("Unknown filter: " + filter
                    + "; supported: lowpass, timelag, running_median");
        };

        String baseName = target.series().endsWith(suffix)
                ? target.series().substring(0, target.series().length() - suffix.length())
                : target.series();
        return text("Applied " + filter + " to series " + target.series()
                + ", result written to series " + baseName + suffix);
    }

    private record FilterTarget(SensorLineChart chart, String series) {
    }
}

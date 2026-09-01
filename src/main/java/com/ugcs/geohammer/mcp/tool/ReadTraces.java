package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.format.gpr.Trace;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import java.util.List;

public class ReadTraces extends McpTool {

    public ReadTraces(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "read_traces";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Read a window of GPR trace amplitudes as a 2D array, decimated "
                + "by averaging to at most max_traces x max_samples cells. Row i of the result covers "
                + "traces starting at trace_start + i * trace_bucket_size; column j covers samples "
                + "starting at sample_start + j * sample_bucket_size. Use {{get_gpr_info}} first "
                + "for the file dimensions.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "trace_start", "integer", "First trace index, 0-based, default 0.");
        addProperty(schema, "trace_count", "integer", "Number of traces to cover, default all.");
        addProperty(schema, "sample_start", "integer", "First sample index, 0-based, default 0.");
        addProperty(schema, "sample_count", "integer", "Number of samples to cover, default all.");
        addProperty(schema, "max_traces", "integer",
                "Maximum rows in the result, default 200, maximum 500.");
        addProperty(schema, "max_samples", "integer",
                "Maximum columns in the result, default 200, maximum 500.");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int traceStart = args.path("trace_start").asInt(0);
        int traceCount = args.path("trace_count").asInt(Integer.MAX_VALUE);
        int sampleStart = args.path("sample_start").asInt(0);
        int sampleCount = args.path("sample_count").asInt(Integer.MAX_VALUE);
        int maxTraces = Math.clamp(args.path("max_traces").asInt(200), 1, 500);
        int maxSamples = Math.clamp(args.path("max_samples").asInt(200), 1, 500);
        if (traceStart < 0 || traceCount < 0 || sampleStart < 0 || sampleCount < 0) {
            throw new IllegalArgumentException("Trace and sample ranges must be non-negative");
        }
        return text(inFxThread(() -> {
            TraceFile traceFile = resolveGprFile(fileName);
            List<Trace> traces = traceFile.getTraces();
            int numTraces = traces.size();
            int numSamples = traceFile.getMaxSamples();

            int traceFrom = Math.min(traceStart, numTraces);
            int traceTo = Math.min(traceFrom + traceCount, numTraces);
            int sampleFrom = Math.min(sampleStart, numSamples);
            int sampleTo = Math.min(sampleFrom + sampleCount, numSamples);

            int traceBucket = Math.max(1, (int) Math.ceil((double) (traceTo - traceFrom) / maxTraces));
            int sampleBucket = Math.max(1, (int) Math.ceil((double) (sampleTo - sampleFrom) / maxSamples));

            ObjectNode result = mapper.createObjectNode();
            result.put("traces", numTraces);
            result.put("samplesPerTrace", numSamples);
            result.put("trace_start", traceFrom);
            result.put("trace_count", traceTo - traceFrom);
            result.put("sample_start", sampleFrom);
            result.put("sample_count", sampleTo - sampleFrom);
            result.put("trace_bucket_size", traceBucket);
            result.put("sample_bucket_size", sampleBucket);

            ArrayNode rows = result.putArray("values");
            for (int t = traceFrom; t < traceTo; t += traceBucket) {
                ArrayNode row = rows.addArray();
                int tTo = Math.min(t + traceBucket, traceTo);
                for (int s = sampleFrom; s < sampleTo; s += sampleBucket) {
                    int sTo = Math.min(s + sampleBucket, sampleTo);
                    double sum = 0;
                    int n = 0;
                    for (int ti = t; ti < tTo; ti++) {
                        Trace trace = traces.get(ti);
                        int limit = Math.min(sTo, trace.numSamples());
                        for (int si = s; si < limit; si++) {
                            sum += trace.getSample(si);
                            n++;
                        }
                    }
                    if (n == 0) {
                        row.addNull();
                    } else {
                        row.add(sum / n);
                    }
                }
            }
            return toJson(result);
        }));
    }
}

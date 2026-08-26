package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.TraceTransform;

public class CropGprSamples extends McpTool {

    private final TraceTransform traceTransform;

    public CropGprSamples(Model model, TraceTransform traceTransform) {
        super(model);
        this.traceTransform = traceTransform;
    }

    @Override
    public String getName() {
        return "crop_gpr_samples";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Crop the sample (time/depth) range of an open GPR file: keeps "
                + "samples [offset, offset + length) of every trace and discards the rest. "
                + "Use to cut away ringing at the top or noise below the depth of interest. "
                + "Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        addProperty(schema, "offset", "integer", "First sample index to keep, 0-based.");
        addProperty(schema, "length", "integer", "Number of samples to keep.");
        schema.putArray("required").add("offset").add("length");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        int offset = args.path("offset").asInt(-1);
        int length = args.path("length").asInt(-1);
        if (offset < 0 || length < 1) {
            throw new IllegalArgumentException("offset must be non-negative and length positive");
        }
        return text(inFxThread(() -> {
            TraceFile traceFile = resolveGprFile(fileName);
            if (traceFile.getMetaFile() == null) {
                throw new IllegalArgumentException("File does not support sample cropping "
                        + "(it has no metadata sidecar)");
            }
            int samples = traceFile.getMaxSamples();
            if (offset >= samples) {
                throw new IllegalArgumentException("offset " + offset + " is out of bounds, "
                        + "traces have " + samples + " samples");
            }
            traceTransform.cropGprSamples(traceFile, offset, length);
            return "Cropped traces to samples [" + offset + ", "
                    + Math.min(offset + length, samples) + ")";
        }));
    }
}

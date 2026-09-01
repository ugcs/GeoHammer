package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.util.Templates;
import java.io.File;

public class GetGprInfo extends McpTool {

    public GetGprInfo(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "get_gpr_info";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Summary of an open GPR (ground penetrating radar) file. "
                + "A GPR file is a sequence of traces; each trace is a column of amplitude samples "
                + "along the time/depth axis recorded at one surface position. Returns the number of "
                + "traces, samples per trace, sample interval, depth scale (centimeters per sample "
                + "in air and in ground, samples per meter), whether background noise was already "
                + "removed, line count and unsaved status.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        return text(inFxThread(() -> {
            TraceFile traceFile = resolveGprFile(fileName);
            ObjectNode result = mapper.createObjectNode();
            File file = traceFile.getFile();
            result.put("name", file != null ? file.getName() : null);
            result.put("template", Templates.getTemplateName(traceFile));
            result.put("traces", traceFile.numTraces());
            result.put("samplesPerTrace", traceFile.getMaxSamples());
            result.put("sampleInterval", traceFile.getSampleInterval());
            result.put("cmPerSampleInAir", traceFile.getSamplesToCmAir());
            result.put("cmPerSampleInGround", traceFile.getSamplesToCmGrn());
            result.put("samplesPerMeter", traceFile.getSamplesPerMeter());
            result.put("backgroundRemoved", traceFile.isBackgroundRemoved());
            result.put("lines", traceFile.getLineRanges().size());
            result.put("unsaved", traceFile.isUnsaved());
            return toJson(result);
        }));
    }
}

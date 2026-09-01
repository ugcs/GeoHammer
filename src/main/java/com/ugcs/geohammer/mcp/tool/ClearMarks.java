package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.chart.Chart;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.element.BaseObject;
import com.ugcs.geohammer.model.element.FoundPlace;
import com.ugcs.geohammer.model.event.WhatChanged;
import java.util.Iterator;
import java.util.List;

public class ClearMarks extends McpTool {

    public ClearMarks(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "clear_marks";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Remove marks (flags) from an open data file. "
                + "Removes all marks when indices are not specified.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        ObjectNode clearIndices = addProperty(schema, "indices", "array",
                "Point indices to remove marks at; all marks are removed when omitted.");
        clearIndices.putObject("items").put("type", "integer");
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        List<Integer> indices = readIndices(args);
        return text(inFxThread(() -> {
            SgyFile dataFile = resolveFile(fileName);
            Chart chart = model.getChart(dataFile);
            int removed = 0;
            Iterator<BaseObject> it = dataFile.getAuxElements().iterator();
            while (it.hasNext()) {
                if (it.next() instanceof FoundPlace flag
                        && (indices == null || indices.contains(flag.getTraceIndex()))) {
                    it.remove();
                    if (chart != null) {
                        chart.removeFlag(flag);
                    }
                    removed++;
                }
            }
            if (removed > 0) {
                dataFile.setUnsaved(true);
                model.updateAuxElements();
                model.publishEvent(new WhatChanged(this, WhatChanged.Change.justdraw));
            }
            return "Removed " + removed + " marks";
        }));
    }
}

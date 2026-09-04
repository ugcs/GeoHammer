package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.TraceFile;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.event.WhatChanged;
import com.ugcs.geohammer.model.undo.UndoModel;

public class RemoveGprBackground extends McpTool {

    private final UndoModel undoModel;

    public RemoveGprBackground(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
    }

    @Override
    public String getName() {
        return "remove_gpr_background";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Remove background noise from an open GPR file by "
                + "subtracting the average horizontal profile from all traces. Same as the "
                + "'Remove background' button in the UI. Supports undo.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        String fileName = optionalString(args, "file");
        TraceFile traceFile = inFxThread(() -> resolveGprFile(fileName));
        if (traceFile.isBackgroundRemoved()) {
            return text("Background is already removed for this file");
        }
        // heavy computation runs on a background thread, as the UI tool does
        traceFile.removeBackground(undoModel);
        inFxThread(() -> {
            model.publishEvent(new WhatChanged(this, WhatChanged.Change.traceValues));
            return null;
        });
        return text("Background noise removed");
    }
}

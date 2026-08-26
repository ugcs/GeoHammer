package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.UndoModel;

public class Undo extends McpTool {

    private final UndoModel undoModel;

    public Undo(Model model, UndoModel undoModel) {
        super(model);
        this.undoModel = undoModel;
    }

    @Override
    public String getName() {
        return "undo";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Undo the last data modification, same as the undo button in the UI. "
                + "Undoable tools: {{write_series}}, {{create_series}}, {{remove_series}}, "
                + "{{cut_to_lines}}, {{split_line}}, "
                + "{{merge_lines}}, {{delete_line}}, {{crop_by_region}}, {{crop_gpr_samples}}, "
                + "{{remove_gpr_background}}. "
                + "{{apply_filter}} and {{run_gridding}} do NOT push an undo step, so calling {{undo}} after a filter "
                + "reverts the last modification made before the filter (for example a preceding cut) and "
                + "leaves the filtered series in place. Reverse a filter with {{remove_series}} instead.");
        tool.set("inputSchema", objectSchema());
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        return text(inFxThread(() -> {
            if (!undoModel.canUndo()) {
                return "Nothing to undo";
            }
            undoModel.undo();
            return undoModel.canUndo()
                    ? "Undone last operation"
                    : "Undone last operation, undo stack is now empty";
        }));
    }
}

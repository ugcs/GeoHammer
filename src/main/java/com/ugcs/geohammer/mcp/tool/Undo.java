package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpSession;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.UndoFrame;
import com.ugcs.geohammer.model.undo.UndoModel;

import java.util.List;

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
        ObjectNode tool = descriptor("Undo the last data modification made by this client. "
                + "A client can undo only its own modifications, newest first: call it again to undo "
                + "earlier ones. The undo is rejected and nothing is changed if a later modification "
                + "by another client or in the app follows it in the GeoHammer undo history, since undoing "
                + "it would revert that modification too. The history keeps only recent modifications "
                + "and is lost when the client reconnects or GeoHammer restarts. "
                + "Undoable tools: {{write_series}}, {{create_series}}, {{remove_series}}, {{import_csv}}, "
                + "{{cut_to_lines}}, {{split_line}}, "
                + "{{merge_lines}}, {{delete_line}}, {{crop_by_region}}, {{crop_gpr_samples}}, "
                + "{{remove_gpr_background}}, {{run_script}} (scripts that modify the file). "
                + "{{apply_filter}}, {{place_marks}}, {{clear_marks}} and {{run_gridding}} do NOT push an "
                + "undo step, so calling {{undo}} after a filter reverts this client's modification made "
                + "before the filter (for example a preceding cut) and leaves the filtered series in place. "
                + "Reverse a filter with {{remove_series}} instead.");
        tool.set("inputSchema", objectSchema());
        return tool;
    }

    @Override
    protected boolean modifiesFiles() {
        return true;
    }

    // file snapshots restore the versions of the files
    @Override
    protected boolean restoresVersions() {
        return true;
    }

    // a rejected undo throws, so that the versions of the files stay unchanged
    @Override
    public ObjectNode invoke(McpSession session, JsonNode args) throws Exception {
        UndoFrame frame = session.peekUndoFrame(undoModel);
        if (frame == null) {
            return text("Nothing to undo: this client has no modifications that can be undone");
        }
        // files are locked for the frame found in getFilesToLock,
        // a concurrent call of the same client may have pushed another one since
        for (SgyFile file : undoModel.getFiles(frame)) {
            if (!file.isLockedByCurrentThread()) {
                throw new IllegalArgumentException("Nothing undone: the last modification of this client "
                        + "changed while waiting for its files, call undo again");
            }
        }
        return text(inFxThread(() -> {
            if (!undoModel.undo(frame)) {
                throw new IllegalArgumentException("Nothing undone: the last modification of this client "
                        + "is followed by a modification of another client or in the app");
            }
            return "Undone the last modification of this client";
        }));
    }

    // files of the last modification of the session
    @Override
    protected List<SgyFile> getFilesToLock(McpSession session, JsonNode args) {
        UndoFrame frame = session.peekUndoFrame(undoModel);
        return frame != null ? undoModel.getFiles(frame) : List.of();
    }
}

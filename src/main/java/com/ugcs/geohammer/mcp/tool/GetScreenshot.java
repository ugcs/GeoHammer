package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.AppContext;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import javafx.scene.Node;

public class GetScreenshot extends McpTool {

    public GetScreenshot(Model model) {
        super(model);
    }

    @Override
    public String getName() {
        return "get_screenshot";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Screenshot of the whole GeoHammer window: "
                + "map, charts and tool panels as the user sees them.");
        tool.set("inputSchema", objectSchema());
        return tool;
    }

    @Override
    public ObjectNode invoke(JsonNode args) throws Exception {
        return inFxThread(() -> {
            if (AppContext.stage == null || AppContext.stage.getScene() == null) {
                throw new IllegalStateException("Application window is not available");
            }
            Node root = AppContext.stage.getScene().getRoot();
            return image(snapshotNode(root), "GeoHammer window");
        });
    }
}

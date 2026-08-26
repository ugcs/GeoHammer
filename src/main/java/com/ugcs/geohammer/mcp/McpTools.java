package com.ugcs.geohammer.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ugcs.geohammer.map.layer.GridLayer;
import com.ugcs.geohammer.mcp.tool.ApplyFilter;
import com.ugcs.geohammer.mcp.tool.ClearMarks;
import com.ugcs.geohammer.mcp.tool.CreateScript;
import com.ugcs.geohammer.mcp.tool.CreateSeries;
import com.ugcs.geohammer.mcp.tool.CropByRegion;
import com.ugcs.geohammer.mcp.tool.CropGprSamples;
import com.ugcs.geohammer.mcp.tool.CutToLines;
import com.ugcs.geohammer.mcp.tool.DeleteLine;
import com.ugcs.geohammer.mcp.tool.ExportCsv;
import com.ugcs.geohammer.mcp.tool.GetChartImage;
import com.ugcs.geohammer.mcp.tool.GetFileInfo;
import com.ugcs.geohammer.mcp.tool.GetGprInfo;
import com.ugcs.geohammer.mcp.tool.GetGridImage;
import com.ugcs.geohammer.mcp.tool.GetScreenshot;
import com.ugcs.geohammer.mcp.tool.GetScript;
import com.ugcs.geohammer.mcp.tool.GetSeriesStats;
import com.ugcs.geohammer.mcp.tool.ListFiles;
import com.ugcs.geohammer.mcp.tool.ListLines;
import com.ugcs.geohammer.mcp.tool.ListScripts;
import com.ugcs.geohammer.mcp.tool.ListSeries;
import com.ugcs.geohammer.mcp.tool.MergeLines;
import com.ugcs.geohammer.mcp.tool.PlaceMarks;
import com.ugcs.geohammer.mcp.tool.ReadData;
import com.ugcs.geohammer.mcp.tool.ReadSeries;
import com.ugcs.geohammer.mcp.tool.ReadTraces;
import com.ugcs.geohammer.mcp.tool.RemoveGprBackground;
import com.ugcs.geohammer.mcp.tool.RemoveSeries;
import com.ugcs.geohammer.mcp.tool.RunGridding;
import com.ugcs.geohammer.mcp.tool.RunScript;
import com.ugcs.geohammer.mcp.tool.SelectFile;
import com.ugcs.geohammer.mcp.tool.SelectSeries;
import com.ugcs.geohammer.mcp.tool.SetPythonPath;
import com.ugcs.geohammer.mcp.tool.SplitLine;
import com.ugcs.geohammer.mcp.tool.Undo;
import com.ugcs.geohammer.mcp.tool.WriteSeries;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.model.undo.UndoModel;
import com.ugcs.geohammer.service.TraceTransform;
import com.ugcs.geohammer.service.gridding.GriddingService;
import com.ugcs.geohammer.service.script.PythonService;
import com.ugcs.geohammer.service.script.ScriptCoordinator;
import com.ugcs.geohammer.service.script.ScriptMetadataLoader;
import com.ugcs.geohammer.service.script.ScriptPaths;
import com.ugcs.geohammer.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Registry of MCP tools: owns the tool inventory, serves tools/list and dispatches tools/call.
@Component
public class McpTools {

    private static final Logger log = LoggerFactory.getLogger(McpTools.class);

    // {{tool_name}} in a tool description, resolved against the registry
    private static final Pattern TOOL_REFERENCE = Pattern.compile("\\{\\{([a-z0-9_]+)}}");

    private static final Pattern TOOL_NAME = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    public McpTools(Model model, UndoModel undoModel, TraceTransform traceTransform,
                    GriddingService griddingService, GridLayer gridLayer,
                    ScriptCoordinator scriptCoordinator, ScriptMetadataLoader scriptMetadataLoader,
                    ScriptPaths scriptPaths, PythonService pythonService) {
        register(new ListFiles(model));
        register(new ListSeries(model));
        register(new ReadSeries(model));
        register(new WriteSeries(model, undoModel));
        register(new PlaceMarks(model));
        register(new ClearMarks(model));
        register(new SelectFile(model));
        register(new RemoveSeries(model, undoModel));
        register(new SelectSeries(model));
        register(new CreateSeries(model, undoModel));
        register(new ApplyFilter(model));
        register(new RunGridding(model, griddingService, gridLayer));
        register(new ListLines(model));
        register(new SplitLine(model, traceTransform));
        register(new MergeLines(model, traceTransform));
        register(new DeleteLine(model, traceTransform));
        register(new CropByRegion(model, traceTransform));
        register(new ReadData(model));
        register(new ExportCsv(model));
        register(new GetSeriesStats(model));
        register(new GetFileInfo(model));
        register(new GetGridImage(model, gridLayer));
        register(new GetChartImage(model));
        register(new GetScreenshot(model));
        register(new Undo(model, undoModel));
        register(new CutToLines(model, undoModel));
        register(new GetGprInfo(model));
        register(new ReadTraces(model));
        register(new RemoveGprBackground(model, undoModel));
        register(new CropGprSamples(model, traceTransform));
        register(new ListScripts(model, scriptMetadataLoader, scriptPaths));
        register(new GetScript(model, scriptMetadataLoader, scriptPaths));
        register(new RunScript(model, scriptMetadataLoader, scriptPaths, scriptCoordinator));
        register(new CreateScript(model, scriptMetadataLoader, scriptPaths));
        register(new SetPythonPath(model, pythonService));
    }

    private void register(McpTool tool) {
        String name = tool.getName();
        if (!TOOL_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Invalid MCP tool name: " + name);
        }
        McpTool previous = tools.put(name, tool);
        if (previous != null) {
            throw new IllegalStateException("Duplicate MCP tool name: " + name);
        }
    }

    public ArrayNode listTools() {
        ArrayNode list = mapper.createArrayNode();
        for (McpTool tool : tools.values()) {
            ObjectNode descriptor = tool.buildSchema();
            resolveReferences(tool.getName(), descriptor);
            checkRequiredProperties(tool.getName(), descriptor);
            list.add(descriptor);
        }
        return list;
    }

    public ObjectNode callTool(JsonNode params) {
        String name = params.path("name").asText(Strings.empty());
        JsonNode args = params.path("arguments");
        McpTool tool = tools.get(name);
        if (tool == null) {
            return toolResult("Unknown tool: " + name, true);
        }
        try {
            return tool.invoke(args);
        } catch (IllegalArgumentException e) {
            // invalid tool arguments, report back to the client
            return toolResult(resolveReferences(name, message(e)), true);
        } catch (Exception e) {
            log.error("MCP tool call failed: " + name, e);
            return toolResult(resolveReferences(name, message(e)), true);
        }
    }

    private static String message(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    // replaces {{tool_name}} placeholders with actual tool names; an unknown reference
    // is logged and left in place, so a stale link never breaks the server
    private void resolveReferences(String toolName, JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> property : object.properties()) {
                JsonNode value = property.getValue();
                if (value.isTextual()) {
                    property.setValue(TextNode.valueOf(resolveReferences(toolName, value.asText())));
                } else {
                    resolveReferences(toolName, value);
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                JsonNode item = array.get(i);
                if (item.isTextual()) {
                    array.set(i, TextNode.valueOf(resolveReferences(toolName, item.asText())));
                } else {
                    resolveReferences(toolName, item);
                }
            }
        }
    }

    private String resolveReferences(String toolName, String text) {
        Matcher matcher = TOOL_REFERENCE.matcher(text);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String reference = matcher.group(1);
            boolean known = tools.containsKey(reference);
            if (!known) {
                log.error("MCP tool {} references unknown tool: {}", toolName, reference);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(known ? reference : matcher.group()));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    // a required argument that is not declared as a property is never validated
    private void checkRequiredProperties(String toolName, ObjectNode descriptor) {
        JsonNode schema = descriptor.path("inputSchema");
        JsonNode properties = schema.path("properties");
        for (JsonNode required : schema.path("required")) {
            if (!properties.has(required.asText())) {
                log.error("MCP tool {} requires an undeclared argument: {}", toolName, required.asText());
            }
        }
    }

    private ObjectNode toolResult(String text, boolean isError) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode content = result.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", text);
        if (isError) {
            result.put("isError", true);
        }
        return result;
    }
}

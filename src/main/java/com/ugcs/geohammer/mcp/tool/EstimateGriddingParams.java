package com.ugcs.geohammer.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.mcp.McpSession;
import com.ugcs.geohammer.mcp.McpTool;
import com.ugcs.geohammer.model.Model;
import com.ugcs.geohammer.service.gridding.GriddingParams;
import com.ugcs.geohammer.service.survey.SurveyService;

import java.util.OptionalDouble;

public class EstimateGriddingParams extends McpTool {

    private final SurveyService surveyService;

    public EstimateGriddingParams(Model model, SurveyService surveyService) {
        super(model);
        this.surveyService = surveyService;
    }

    @Override
    public String getName() {
        return "estimate_gridding_params";
    }

    @Override
    public ObjectNode buildSchema() {
        ObjectNode tool = descriptor("Estimate gridding parameters of an open data file from the spacing "
                + "of its survey lines. The lines are detected from the track geometry; the line structure "
                + "of the file is not used. Returns cell_size (a fifth of the line spacing) and "
                + "blanking_distance (the line spacing), both rounded to 2 decimal places and kept within "
                + "0.01 to 100, and line_spacing as estimated, without rounding; all in meters. Pass "
                + "cell_size and blanking_distance to {{run_gridding}}. Does not run gridding and does not "
                + "change the file, its grid or saved parameters. Fails when the track has no parallel "
                + "survey lines.");
        ObjectNode schema = objectSchema();
        addFileProperty(schema);
        tool.set("inputSchema", schema);
        return tool;
    }

    @Override
    public ObjectNode invoke(McpSession session, JsonNode args) throws Exception {
        SgyFile dataFile = resolveFile(optionalString(args, "file"));
        OptionalDouble lineSpacing = surveyService.estimateLineSpacing(dataFile);
        if (lineSpacing.isEmpty()) {
            throw new IllegalArgumentException("No survey lines detected: "
                    + "the track of the file has no parallel lines");
        }
        GriddingParams params = GriddingParams.fromLineSpacing(lineSpacing.getAsDouble());

        ObjectNode result = mapper.createObjectNode();
        result.put("cell_size", params.cellSize());
        result.put("blanking_distance", params.blankingDistance());
        result.put("line_spacing", lineSpacing.getAsDouble());
        return text(toJson(result));
    }
}

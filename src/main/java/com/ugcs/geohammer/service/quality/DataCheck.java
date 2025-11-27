package com.ugcs.geohammer.service.quality;

import com.ezylang.evalex.BaseException;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.format.csv.CsvFile;
import com.ugcs.geohammer.model.LatLon;
import com.google.common.base.Strings;
import com.ugcs.geohammer.format.GeoData;
import com.ugcs.geohammer.model.template.Template;
import com.ugcs.geohammer.util.Check;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataCheck extends FileQualityCheck {

    private static final Logger log = LoggerFactory.getLogger(DataCheck.class);

    private static final double MIN_RADIUS = 0.15;

    private final double radius;

    public DataCheck(double radius) {
        this.radius = Math.max(radius, MIN_RADIUS);
    }

    @Override
    public List<QualityIssue> checkFile(SgyFile file) {
        if (file instanceof CsvFile csvFile) {
            Template template = csvFile.getTemplate();
            DataValidation validation = DataCheck.buildDataValidation(template);
            return validation != null
                    ? checkValues(csvFile.getGeoData(), validation)
                    : List.of();
        } else {
            return List.of();
        }
    }

    private List<QualityIssue> checkValues(List<GeoData> values, DataValidation validation) {
        if (values == null) {
            return List.of();
        }
        if (validation == null) {
            return List.of();
        }

        List<QualityIssue> issues = new ArrayList<>();
        GeoData lastProblem = null;

        Expression expression = new Expression(validation.getExpression());
        Map<String, Number> varValues = new HashMap<>();
        for (GeoData value : values) {
            if (isInRange(value, lastProblem)) {
                // skip sample
                continue;
            }
            varValues.clear();
            if (validation.getVarHeaders() != null) {
                boolean varMissing = false;
                for (Map.Entry<String, String> e : validation.getVarHeaders().entrySet()) {
                    String varName = e.getKey();
                    String varHeader = e.getValue();
                    Number varValue = value.getNumber(varHeader);
                    if (varValue == null) {
                        varMissing = true;
                        break;
                    }
                    varValues.put(varName, varValue);
                }
                if (varMissing) {
                    // some of the data validation columns are missing;
                    // do not try to validate value
                    continue;
                }
            }
            boolean dataValid = true;
            try {
                EvaluationValue result = expression.copy().withValues(varValues).evaluate();
                dataValid = result.getBooleanValue();
            } catch (BaseException e) {
                log.warn("Cannot evaluate expression", e);
                // in case of expression error data is not marked as invalid
            }
            if (!dataValid) {
                issues.add(createDataIssue(value));
                lastProblem = value;
            }
        }
        return issues;
    }

    private boolean isInRange(GeoData value, GeoData last) {
        if (last == null) {
            return false;
        }
        if (!Objects.equals(value.getLine(), last.getLine())) {
            return false;
        }
        LatLon latlon = new LatLon(value.getLatitude(), value.getLongitude());
        LatLon lastLatLon = new LatLon(last.getLatitude(), last.getLongitude());
        return latlon.getDistance(lastLatLon) <= radius;
    }

    private QualityIssue createDataIssue(GeoData value) {
        Coordinate center = new Coordinate(value.getLongitude(), value.getLatitude());
        return new PointQualityIssue(
                QualityColors.DATA,
                center,
                radius
        );
    }

    public static DataValidation buildDataValidation(Template template) {
        if (template == null) {
            return null;
        }
        if (Strings.isNullOrEmpty(template.getDataValidation())) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template.getDataValidation());

        // var: name -> header
        StringBuilder expression = new StringBuilder();
        Map<String, String> varHeaders = new HashMap<>();
        while (matcher.find()) {
            String varName = "v" + (varHeaders.size() + 1);
            String varHeader = Strings.nullToEmpty(matcher.group(1)).trim();
            Check.notEmpty(varHeader, "Expression error: empty header");
            varHeaders.put(varName, varHeader);
            matcher.appendReplacement(expression, varName);
        }
        matcher.appendTail(expression);
        return new DataValidation(expression.toString(), varHeaders);
    }

    public static class DataValidation {

        // expression text
        private final String expression;

        // variable name to header mapping
        private final Map<String, String> varHeaders;

        public DataValidation(String expression, Map<String, String> varHeaders) {
            Check.notEmpty(expression);

            this.expression = expression;
            this.varHeaders = varHeaders;
        }

        public String getExpression() {
            return expression;
        }

        public Map<String, String> getVarHeaders() {
            return varHeaders;
        }
    }
}

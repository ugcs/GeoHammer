package com.ugcs.gprvisualizer.app.quality;

import com.ezylang.evalex.BaseException;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.data.EvaluationValue;
import com.github.thecoldwine.sigrun.common.ext.CsvFile;
import com.github.thecoldwine.sigrun.common.ext.LatLon;
import com.google.common.base.Strings;
import com.ugcs.gprvisualizer.app.parcers.GeoData;
import com.ugcs.gprvisualizer.app.parcers.Semantic;
import com.ugcs.gprvisualizer.app.parcers.SensorValue;
import com.ugcs.gprvisualizer.app.yaml.Template;
import com.ugcs.gprvisualizer.utils.Check;
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
    public List<QualityIssue> checkFile(CsvFile file) {
        if (file == null) {
            return List.of();
        }

        Template template = file.getTemplate();
        DataValidation validation = DataCheck.buildDataValidation(template);
        if (validation == null) {
            return List.of();
        }

        return checkValues(file.getGeoData(), template, validation);
    }

    private List<QualityIssue> checkValues(List<GeoData> values, Template template, DataValidation validation) {
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
            if (isInRange(value, lastProblem, template)) {
                // skip sample
                continue;
            }
            varValues.clear();
            if (validation.getVarSemantics() != null) {
                boolean varMissing = false;
                for (Map.Entry<String, String> e : validation.getVarSemantics().entrySet()) {
                    String varName = e.getKey();
                    String varSemantic = e.getValue();
                    String varHeader = GeoData.getHeader(varSemantic, template);
                    SensorValue sensorValue = value.getSensorValue(varHeader);
                    if (sensorValue == null || sensorValue.data() == null) {
                        varMissing = true;
                        break;
                    }
                    varValues.put(varName, sensorValue.data());
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

    private boolean isInRange(GeoData value, GeoData last, Template template) {
        if (last == null) {
            return false;
        }
        String lineHeader = GeoData.getHeader(Semantic.LINE, template);
        if (!Objects.equals(value.getInt(lineHeader), last.getInt(lineHeader))) {
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

        // var: name -> semantic
        StringBuilder expression = new StringBuilder();
        Map<String, String> varSemantics = new HashMap<>();
        while (matcher.find()) {
            String varName = "v" + (varSemantics.size() + 1);
            String varSemantic = Strings.nullToEmpty(matcher.group(1)).trim();
            Check.notEmpty(varSemantic, "Expression error: empty semantic");
            varSemantics.put(varName, varSemantic);
            matcher.appendReplacement(expression, varName);
        }
        matcher.appendTail(expression);
        return new DataValidation(expression.toString(), varSemantics);
    }

    public static class DataValidation {

        // expression text
        private final String expression;

        // variable name to semantic mapping
        private final Map<String, String> varSemantics;

        public DataValidation(String expression, Map<String, String> varSemantics) {
            Check.notEmpty(expression);

            this.expression = expression;
            this.varSemantics = varSemantics;
        }

        public String getExpression() {
            return expression;
        }

        public Map<String, String> getVarSemantics() {
            return varSemantics;
        }
    }
}

package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.csv.CsvFile;

import java.util.ArrayList;
import java.util.List;

public class QualityControl {

    public List<QualityIssue> getQualityIssues(List<CsvFile> files, List<QualityCheck> checks) {

        List<QualityIssue> result = new ArrayList<>();
        for (QualityCheck check : checks) {
            List<QualityIssue> issues = check.check(files);
            if (issues == null || issues.isEmpty()) {
                continue;
            }
            result.addAll(issues);
        }
        return result;
    }
}

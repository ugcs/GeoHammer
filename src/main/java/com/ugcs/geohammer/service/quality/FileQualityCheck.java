package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.SgyFile;

import java.util.ArrayList;
import java.util.List;

// isolated per-file quality check
public abstract class FileQualityCheck implements QualityCheck {

    @Override
    public List<QualityIssue> check(List<SgyFile> files) {
        if (files == null) {
            return List.of();
        }

        List<QualityIssue> issues = new ArrayList<>();
        for (SgyFile file : files) {
            issues.addAll(checkFile(file));
        }
        return issues;
    }

    public abstract List<QualityIssue> checkFile(SgyFile file);
}

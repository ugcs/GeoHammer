package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.csv.CsvFile;

import java.util.List;

public interface QualityCheck {

    List<QualityIssue> check(List<CsvFile> files);
}

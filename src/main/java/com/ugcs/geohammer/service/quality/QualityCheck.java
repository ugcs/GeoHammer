package com.ugcs.geohammer.service.quality;

import com.ugcs.geohammer.format.SgyFile;

import java.util.List;

public interface QualityCheck {

    List<QualityIssue> check(List<SgyFile> files);
}

package com.ugcs.geohammer.geotagger.extraction;

import java.util.List;

import com.ugcs.geohammer.format.SgyFile;
import com.ugcs.geohammer.geotagger.domain.Position;

public interface PositionExtractor {
	List<Position> extractFrom(SgyFile file);
	List<Position> extractFrom(List<SgyFile> files);
}

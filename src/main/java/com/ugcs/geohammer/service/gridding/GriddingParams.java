package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.util.Check;
import com.ugcs.geohammer.util.Numbers;

public record GriddingParams (
        double cellSize,
        double blankingDistance
) {

	public static final GriddingParams DEFAULT = new GriddingParams(0.1, 1.0);

	public static final double MAX_CELL_SIZE = 100.0;

	public static final double MAX_BLANKING_DISTANCE = 100.0;

	public GriddingParams {
		Check.condition(cellSize > 0);
		Check.condition(blankingDistance > 0);
	}

	public static GriddingParams fromLineSpacing(double lineSpacing) {
		Check.condition(lineSpacing > 0);

		double cellSize = Numbers.round(0.2 * lineSpacing, 2);
		cellSize = Math.clamp(cellSize, 0.01, MAX_CELL_SIZE);

		double blankingDistance = Numbers.round(lineSpacing, 2);
		blankingDistance = Math.clamp(blankingDistance, 0.01, MAX_BLANKING_DISTANCE);

		return new GriddingParams(cellSize, blankingDistance);
	}
}

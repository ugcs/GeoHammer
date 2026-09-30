package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.util.Check;

public record GriddingParams (
        double cellSize,
        double blankingDistance
) {

	public GriddingParams {
		Check.condition(cellSize > 0);
		Check.condition(blankingDistance > 0);
	}
}

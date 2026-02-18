package com.ugcs.geohammer.service.palette;

import java.awt.Color;

import com.ugcs.geohammer.model.Range;

public interface Palette {

	Color getColor(double value);

	Range getRange();
}

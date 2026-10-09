package com.ugcs.geohammer.service.magnetics;

public record HeadingCrossover(int firstLine, int secondLine, int firstHeadingBin, int secondHeadingBin,
                               double error) {
}

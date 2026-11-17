package com.ugcs.geohammer.service.gridding;

import com.ugcs.geohammer.util.Check;

import java.util.Collection;

final class GridMask {

    private final int blankingRadius;

    private final int margin;

    // blanking radius and margin are in cells; data cells up to two blanking radii apart
    // are joined, the margin is shown outside the outline of the data cells
    GridMask(int blankingRadius, int margin) {
        Check.condition(blankingRadius >= 0);
        Check.condition(margin >= 0);

        this.blankingRadius = blankingRadius;
        this.margin = margin;
    }

    boolean[][] create(int width, int height, Collection<CellIndex> dataCells) {
        Check.condition(width >= 0 && height >= 0);
        Check.notNull(dataCells);

        // a radius above the grid size gives the same mask as the grid size
        int radius = Math.min(blankingRadius, Math.max(width, height));
        // add temporary padding for shrinking correctness
		boolean[][] padded = new boolean[width + 2 * radius][height + 2 * radius];
        for (CellIndex cell : dataCells) {
            padded[cell.x() + radius][cell.y() + radius] = true;
        }
        // spread the data cells by the radius
        // and shrink the result back by the same radius
        spread(padded, radius, true);
        spread(padded, radius, false);
        spread(padded, margin, true);
        // remove temporary padding
        boolean[][] mask = new boolean[width][height];
        for (int x = 0; x < width; x++) {
            System.arraycopy(padded[x + radius], radius, mask[x], 0, height);
        }
        return mask;
    }

    // sets the visibility to the cells that have a cell
    // of this visibility within the radius in a square neighbourhood
    private static void spread(boolean[][] mask, int radius, boolean visible) {
        int width = mask.length;
        int height = width > 0 ? mask[0].length : 0;
        // distance to the last cell of this visibility, when it is outside the radius
        int far = radius + 1;
        int outsideDistance = visible ? far : 0;

        boolean[] near = new boolean[Math.max(width, height)];
        for (int x = 0; x < width; x++) {
            int distance = outsideDistance;
            for (int y = 0; y < height; y++) {
                distance = mask[x][y] == visible ? 0 : Math.min(distance + 1, far);
                near[y] = distance <= radius;
            }
            distance = outsideDistance;
            for (int y = height - 1; y >= 0; y--) {
                distance = mask[x][y] == visible ? 0 : Math.min(distance + 1, far);
                if (near[y] || distance <= radius) {
                    mask[x][y] = visible;
                }
            }
        }
        for (int y = 0; y < height; y++) {
            int distance = outsideDistance;
            for (int x = 0; x < width; x++) {
                distance = mask[x][y] == visible ? 0 : Math.min(distance + 1, far);
                near[x] = distance <= radius;
            }
            distance = outsideDistance;
            for (int x = width - 1; x >= 0; x--) {
                distance = mask[x][y] == visible ? 0 : Math.min(distance + 1, far);
                if (near[x] || distance <= radius) {
                    mask[x][y] = visible;
                }
            }
        }
    }
}

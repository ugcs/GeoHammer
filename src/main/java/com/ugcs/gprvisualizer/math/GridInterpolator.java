package com.ugcs.gprvisualizer.math;

import java.util.PriorityQueue;

public final class GridInterpolator {

    private GridInterpolator() {
    }

    private record NeighborSum(double sum, double weightSum) {

        float getAverage() {
            return weightSum > 0 ? (float)(sum / weightSum) : Float.NaN;
        }
    }

    private record Cell(int i, int j, double neighborWeight) implements Comparable<Cell> {
        @Override
        public int compareTo(Cell other) {
            // Higher weight = higher priority
            return Double.compare(other.neighborWeight, this.neighborWeight);
        }
    }

    public static void interpolate(float[][] grid, double cellWidth, double cellHeigh) {
        int m = grid.length;
        int n = grid[0].length;

        double[][] neighborWeights = new double[m][n];
        PriorityQueue<Cell> queue = new PriorityQueue<>();

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (!Float.isNaN(grid[i][j])) {
                    continue;
                }

                NeighborSum ns = getNeighborSum(grid, i, j, cellWidth, cellHeigh);
                double w = ns.weightSum;
                if (w > 0.0) {
                    queue.add(new Cell(i, j, w));
                }
                neighborWeights[i][j] = w;
            }
        }

        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            int i = cell.i();
            int j = cell.j();

            // as one cell may be added multiple times to a queue
            if (!Float.isNaN(grid[i][j])) {
                continue;
            }

            NeighborSum neighborSum = getNeighborSum(grid, i, j, cellWidth, cellHeigh);
            grid[i][j] = neighborSum.getAverage();

            // Update neighbors’ weights
            for (int di = -1; di <= 1; di++) {
                for (int dj = -1; dj <= 1; dj++) {
                    if (di == 0 && dj == 0) {
                        continue;
                    }

                    int ni = i + di;
                    int nj = j + dj;
                    if (ni < 0 || ni >= m || nj < 0 || nj >= n) {
                        continue;
                    }

                    if (!Float.isNaN(grid[ni][nj])) {
                        continue;
                    }

                    double dx = di * cellWidth;
                    double dy = dj * cellHeigh;
                    double increment = 1.0 / Math.sqrt(dx * dx + dy * dy);

                    neighborWeights[ni][nj] += increment;
                    queue.add(new Cell(ni, nj, neighborWeights[ni][nj]));
                }
            }
        }
    }

    private static NeighborSum getNeighborSum(float[][] grid, int i, int j, double cellWidth, double cellHeight) {
        int m = grid.length;
        int n = grid[0].length;

        double sum = 0.0;
        double weightSum = 0.0;

        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                if (di == 0 && dj == 0) {
                    continue;
                }

                int ni = i + di;
                int nj = j + dj;
                if (ni < 0 || ni >= m || nj < 0 || nj >= n) {
                    continue;
                }

                float v = grid[ni][nj];
                if (Float.isNaN(v)) {
                    continue;
                }

                double distanceX = di * cellWidth;
                double distanceY = dj * cellHeight;
                double dist = Math.sqrt(distanceX * distanceX + distanceY * distanceY);
                double weight = 1.0 / dist;
                sum += v * weight;
                weightSum += weight;
            }
        }

        return new NeighborSum(sum, weightSum);
    }
}

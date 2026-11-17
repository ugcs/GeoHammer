package com.ugcs.geohammer.service.gridding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class GridMaskTest {

    private static final int LINE_SPACING = 5;

    private static final int RADIUS = LINE_SPACING;

    private static final int MARGIN = 1;

    // distance from a cell center to a point of the cell
    private static final double CELL_RADIUS = Math.sqrt(0.5);

    @Test
    void create_singleCell_returnsCellWithNeighbours() {
        boolean[][] dataCells = new boolean[9][9];
        dataCells[4][4] = true;

        boolean[][] mask = createMask(dataCells, RADIUS);

        assertMaskIsCellsWithNeighbours(dataCells, mask);
    }

    @Test
    void create_twoCells_returnsCellsWithNeighbours() {
        boolean[][] dataCells = new boolean[20][9];
        dataCells[2][4] = true;
        dataCells[17][4] = true;

        boolean[][] mask = createMask(dataCells, RADIUS);

        assertMaskIsCellsWithNeighbours(dataCells, mask);
    }

    @Test
    void create_straightLine_returnsCellsWithNeighbours() {
        boolean[][] dataCells = new boolean[40][9];
        for (int x = 5; x < 35; x++) {
            dataCells[x][4] = true;
        }

        boolean[][] mask = createMask(dataCells, RADIUS);

        assertMaskIsCellsWithNeighbours(dataCells, mask);
    }

    @Test
    void create_noDataCells_returnsEmptyMask() {
        boolean[][] mask = createMask(new boolean[5][5], RADIUS);

        for (boolean[] column : mask) {
            for (boolean cell : column) {
                assertFalse(cell);
            }
        }
    }

    @Test
    void create_rotatedLines_followsOuterLinesAndLineEnds() {
        RotatedSurvey survey = new RotatedSurvey(30, 60, 8, 120);

        boolean[][] mask = createMask(survey.dataCells(), RADIUS);

        assertContainsDataCells(survey.dataCells(), mask);
        for (int x = 0; x < mask.length; x++) {
            for (int y = 0; y < mask[x].length; y++) {
                double distance = survey.distanceTo(x, y);
                if (distance == 0) {
                    assertTrue(mask[x][y], "cell inside the survey " + x + ", " + y);
                }
                // a data cell center is off its point by up to the cell radius,
                // and its diagonal neighbour is a cell diagonal away
                if (distance > CELL_RADIUS + Math.sqrt(2)) {
                    assertFalse(mask[x][y], "cell outside the survey " + x + ", " + y);
                }
            }
        }
    }

    @Test
    void create_concaveSurvey_excludesNotch() {
        // horizontal lines of an L shape: a wide bottom block and a narrow block above it
        int size = 80;
        boolean[][] dataCells = new boolean[size][size];
        for (int y = 10; y <= 70; y += LINE_SPACING) {
            int maxX = y <= 30 ? 70 : 30;
            for (int x = 10; x <= maxX; x++) {
                dataCells[x][y] = true;
            }
        }

        boolean[][] mask = createMask(dataCells, RADIUS);

        assertContainsDataCells(dataCells, mask);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                boolean inBottomBlock = x >= 10 && x <= 70 && y >= 10 && y <= 30;
                boolean inTopBlock = x >= 10 && x <= 30 && y >= 10 && y <= 70;
                if (inBottomBlock || inTopBlock) {
                    assertTrue(mask[x][y], "cell inside the survey " + x + ", " + y);
                }
                boolean nearBottomBlock = x >= 8 && x <= 72 && y >= 8 && y <= 32;
                boolean nearTopBlock = x >= 8 && x <= 32 && y >= 8 && y <= 72;
                if (!nearBottomBlock && !nearTopBlock) {
                    assertFalse(mask[x][y], "cell outside the survey " + x + ", " + y);
                }
            }
        }
    }

    @Test
    void create_holeWiderThanTwoRadii_excludesHole() {
        // filled block with a square hole
        int size = 80;
        boolean[][] dataCells = new boolean[size][size];
        for (int x = 10; x <= 70; x++) {
            for (int y = 10; y <= 70; y++) {
                boolean inHole = x > 25 && x < 55 && y > 25 && y < 55;
                dataCells[x][y] = !inHole;
            }
        }

        boolean[][] mask = createMask(dataCells, RADIUS);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                boolean nearBlock = x >= 9 && x <= 71 && y >= 9 && y <= 71;
                // hole without the margin of one cell
                boolean inHole = x > 26 && x < 54 && y > 26 && y < 54;
                assertEquals(nearBlock && !inHole, mask[x][y], "cell " + x + ", " + y);
            }
        }
    }

    @Test
    void create_gapOfTwoRadii_fillsGap() {
        boolean[][] dataCells = createLines(10, 10 + 2 * RADIUS + 1);

        boolean[][] mask = createMask(dataCells, RADIUS);

        for (int x = 0; x < dataCells.length; x++) {
            for (int y = 0; y < dataCells[x].length; y++) {
                boolean nearLines = x >= 9 && x <= 10 + 2 * RADIUS + 2 && y >= 9 && y <= 31;
                assertEquals(nearLines, mask[x][y], "cell " + x + ", " + y);
            }
        }
    }

    @Test
    void create_gapWiderThanTwoRadii_excludesGap() {
        boolean[][] dataCells = createLines(10, 10 + 2 * RADIUS + 2);

        boolean[][] mask = createMask(dataCells, RADIUS);

        assertMaskIsCellsWithNeighbours(dataCells, mask);
    }

    @Test
    void create_radiusAboveGridSize_returnsMaskOfGridSizeRadius() {
        boolean[][] dataCells = createLines(10, 30);
        dataCells[20][35] = true;

        boolean[][] mask = createMask(dataCells, 1_000_000);

        assertArrayEquals(createMask(dataCells, dataCells.length), mask);
    }

    @Test
    void create_separateBlocks_excludesSpaceBetween() {
        boolean[][] dataCells = new boolean[100][40];
        for (int y = 10; y <= 30; y++) {
            for (int x = 10; x <= 30; x++) {
                dataCells[x][y] = true;
            }
            for (int x = 70; x <= 90; x++) {
                dataCells[x][y] = true;
            }
        }

        boolean[][] mask = createMask(dataCells, RADIUS);

        for (int x = 0; x < 100; x++) {
            for (int y = 0; y < 40; y++) {
                boolean nearLeftBlock = x >= 9 && x <= 31 && y >= 9 && y <= 31;
                boolean nearRightBlock = x >= 69 && x <= 91 && y >= 9 && y <= 31;
                assertEquals(nearLeftBlock || nearRightBlock, mask[x][y], "cell " + x + ", " + y);
            }
        }
    }

    @Test
    void create_largeRotatedSurvey_completesInTime() {
        RotatedSurvey survey = new RotatedSurvey(30, 1500, 200, 2200);

        boolean[][] mask = assertTimeout(Duration.ofSeconds(2),
                () -> createMask(survey.dataCells(), RADIUS));

        assertContainsDataCells(survey.dataCells(), mask);
    }

    @Test
    void create_largeConcaveSurvey_completesInTime() {
        // horizontal lines of an L shape
        int size = 2000;
        boolean[][] dataCells = new boolean[size][size];
        for (int y = 200; y <= 1800; y += LINE_SPACING) {
            int maxX = y <= 600 ? 1800 : 600;
            for (int x = 200; x <= maxX; x++) {
                dataCells[x][y] = true;
            }
        }

        boolean[][] mask = assertTimeout(Duration.ofSeconds(2),
                () -> createMask(dataCells, RADIUS));

        assertContainsDataCells(dataCells, mask);
        assertFalse(mask[1200][1200]);
    }

    // two lines along the y axis
    private static boolean[][] createLines(int x1, int x2) {
        boolean[][] dataCells = new boolean[40][40];
        for (int y = 10; y <= 30; y++) {
            dataCells[x1][y] = true;
            dataCells[x2][y] = true;
        }
        return dataCells;
    }

    private static boolean[][] createMask(boolean[][] dataCells, int radius) {
        int width = dataCells.length;
        int height = dataCells[0].length;
        List<CellIndex> cells = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (dataCells[x][y]) {
                    cells.add(new CellIndex(x, y));
                }
            }
        }
        return new GridMask(radius, MARGIN).create(width, height, cells);
    }

    private static void assertContainsDataCells(boolean[][] dataCells, boolean[][] mask) {
        for (int x = 0; x < dataCells.length; x++) {
            for (int y = 0; y < dataCells[x].length; y++) {
                if (dataCells[x][y]) {
                    assertTrue(mask[x][y], "data cell " + x + ", " + y);
                }
            }
        }
    }

    private static void assertMaskIsCellsWithNeighbours(boolean[][] dataCells, boolean[][] mask) {
        for (int x = 0; x < dataCells.length; x++) {
            for (int y = 0; y < dataCells[x].length; y++) {
                assertEquals(hasDataCellNear(dataCells, x, y), mask[x][y], "cell " + x + ", " + y);
            }
        }
    }

    private static boolean hasDataCellNear(boolean[][] dataCells, int x, int y) {
        for (int i = Math.max(x - 1, 0); i <= Math.min(x + 1, dataCells.length - 1); i++) {
            for (int j = Math.max(y - 1, 0); j <= Math.min(y + 1, dataCells[i].length - 1); j++) {
                if (dataCells[i][j]) {
                    return true;
                }
            }
        }
        return false;
    }

    // parallel lines at an angle to the grid axes, placed in the middle of a square grid;
    // the survey area is a rectangle of line length by the distance between the outer lines
    private static final class RotatedSurvey {

        private static final double POINT_STEP = 0.5;

        private final double cos;

        private final double sin;

        private final double length;

        private final double width;

        private final double originX;

        private final double originY;

        private final boolean[][] dataCells;

        RotatedSurvey(double angleDegrees, double length, int numLines, int gridSize) {
            this.cos = Math.cos(Math.toRadians(angleDegrees));
            this.sin = Math.sin(Math.toRadians(angleDegrees));
            this.length = length;
            this.width = (numLines - 1) * LINE_SPACING;
            // rectangle center in the grid center
            this.originX = 0.5 * gridSize - 0.5 * (length * cos - width * sin);
            this.originY = 0.5 * gridSize - 0.5 * (length * sin + width * cos);
            this.dataCells = new boolean[gridSize][gridSize];

            for (int line = 0; line < numLines; line++) {
                double across = line * LINE_SPACING;
                for (double along = 0; along <= length; along += POINT_STEP) {
                    int x = (int) Math.round(originX + along * cos - across * sin);
                    int y = (int) Math.round(originY + along * sin + across * cos);
                    dataCells[x][y] = true;
                }
            }
        }

        boolean[][] dataCells() {
            return dataCells;
        }

        // distance from a cell center to the survey rectangle, 0 inside
        double distanceTo(int x, int y) {
            double dx = x - originX;
            double dy = y - originY;
            double along = dx * cos + dy * sin;
            double across = -dx * sin + dy * cos;
            double outsideAlong = Math.max(0, Math.max(-along, along - length));
            double outsideAcross = Math.max(0, Math.max(-across, across - width));
            return Math.hypot(outsideAlong, outsideAcross);
        }
    }
}

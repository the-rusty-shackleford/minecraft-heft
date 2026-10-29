/*
 * Heft - every flat item becomes a solid one.
 * Copyright (C) 2026 Chunkworks
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.heft.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Partitions:
 * - cover: an empty grid; a level square (one rectangle); a level shape with a hole; a curved relief (sphere, loaf) at
 *   tolerance 0 and above; cells reaching the grid's edge
 * - outlines: a rectangle alone (its four corners); one with a neighbour's corner on each kind of side (left, bottom,
 *   right, top); neighbours sharing part of an edge
 * - fan: rings of 3, 4, 5, 6, 7 corners
 * - Rect: empty or inverted
 */
class PatchesTest {
    private static final int N = 32;

    private static boolean[][] circle(double r) {
        boolean[][] s = new boolean[N][N];
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                double x = i + 0.5 - N / 2.0, y = j + 0.5 - N / 2.0;
                s[j][i] = x * x + y * y <= r * r;
            }
        }
        return s;
    }

    private static float[][] level(int n) {
        return new float[n + 1][n + 1];
    }

    /** Checks the covering's invariants: every true cell covered once, no false one, every corner within tol. */
    private static void checkCover(boolean[][] cells, float[][] c, float tol, List<Patches.Rect> rects) {
        int n = cells.length;
        int[][] times = new int[n][n];
        for (Patches.Rect r : rects) {
            for (int y = r.j0(); y < r.j1(); y++) {
                for (int x = r.i0(); x < r.i1(); x++) {
                    times[y][x]++;
                }
            }
            assertTrue(Patches.fits(c, r.i0(), r.j0(), r.i1(), r.j1(), tol), r.toString());
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                assertEquals(cells[j][i] ? 1 : 0, times[j][i], "cell " + i + "," + j);
            }
        }
    }

    private static float[][] corners(Relief relief, boolean[][] cells, int side) {
        return Relief.corners(cells, relief.heights(cells)[side]);
    }

    @Test
    void anEmptyGridHasNoRectangles() {
        assertEquals(List.of(), Patches.cover(new boolean[4][4], level(4), 0F));
    }

    @Test
    void aLevelSquareIsOneRectangle() {
        boolean[][] cells = new boolean[N][N];
        for (boolean[] row : cells) {
            java.util.Arrays.fill(row, true);
        }
        assertEquals(List.of(new Patches.Rect(0, 0, N, N)), Patches.cover(cells, level(N), 0F));
    }

    @Test
    void aLevelShapeWithAHoleIsCoveredExactly() {
        boolean[][] cells = circle(12);
        boolean[][] hole = circle(4);
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                cells[j][i] &= !hole[j][i];
            }
        }
        List<Patches.Rect> rects = Patches.cover(cells, level(N), 0F);
        checkCover(cells, level(N), 0F, rects);
        assertTrue(rects.size() < 60, "rects " + rects.size());
    }

    @Test
    void aCurvedReliefAtToleranceZeroKeepsEveryCornerOnItsSurface() {
        boolean[][] cells = circle(13);
        float[][] c = corners(Relief.SPHERE, cells, 0);
        checkCover(cells, c, 0F, Patches.cover(cells, c, 0F));
    }

    @Test
    void aCurvedReliefWithToleranceTakesUnderHalfTheQuadsAndStaysWithinIt() {
        for (Relief relief : List.of(Relief.SPHERE, Relief.LOAF, Relief.SLAB, Relief.GEM, Relief.BAR)) {
            for (int side = 0; side < 2; side++) {
                boolean[][] cells = circle(13);
                float[][] c = corners(relief, cells, side);
                List<Patches.Rect> rects = Patches.cover(cells, c, 0.1F);
                checkCover(cells, c, 0.1F, rects);
                int cellCount = 0;
                for (boolean[] row : cells) {
                    for (boolean b : row) {
                        cellCount += b ? 1 : 0;
                    }
                }
                int quads = 0;
                for (int[][] ring : Patches.outlines(rects, N)) {
                    quads += Patches.fan(ring.length).size();
                }
                assertTrue(quads * 2 < cellCount, relief + " side " + side + ": " + quads + " quads over " + cellCount + " cells");
            }
        }
    }

    @Test
    void cellsAtTheGridsEdgeAreCovered() {
        boolean[][] cells = new boolean[3][3];
        cells[0][2] = cells[1][2] = cells[2][0] = true;
        List<Patches.Rect> rects = Patches.cover(cells, level(3), 0F);
        checkCover(cells, level(3), 0F, rects);
        assertEquals(List.of(new Patches.Rect(2, 0, 3, 2), new Patches.Rect(0, 2, 1, 3)), rects);
    }

    @Test
    void aRectangleAloneIsItsFourCornersInTheGamesOrder() {
        List<int[][]> rings = Patches.outlines(List.of(new Patches.Rect(1, 2, 4, 5)), 8);
        assertEquals(1, rings.size());
        assertArrayEquals(new int[][] {{1, 2}, {1, 5}, {4, 5}, {4, 2}}, rings.get(0));
    }

    @Test
    void aNeighboursCornerOnAnyEdgeJoinsTheOutline() {
        // A 2 x 2 square in the middle of a 4 x 4 grid, a single cell beside each side's middle... each neighbour 1 x 1,
        // placed so one of its corners falls inside one side of the square.
        Patches.Rect mid = new Patches.Rect(1, 1, 3, 3);
        List<Patches.Rect> rects = List.of(mid,
                new Patches.Rect(0, 1, 1, 2), // left, its corner (1, 2) on the square's left side
                new Patches.Rect(1, 3, 2, 4), // below, its corner (2, 3) on the bottom
                new Patches.Rect(3, 2, 4, 3), // right, its corner (3, 2) on the right side
                new Patches.Rect(2, 0, 3, 1)); // above, its corner (2, 1) on the top
        assertArrayEquals(new int[][] {{1, 1}, {1, 2}, {1, 3}, {2, 3}, {3, 3}, {3, 2}, {3, 1}, {2, 1}}, Patches.outlines(rects, 4).get(0));
    }

    @Test
    void neighboursSharingAnEdgeAgreeOnItsCorners() {
        // Every segment of every outline between two grid corners, with the outline on either side of it, must be
        // drawn by both outlines with the same corners: the surface has no T-junction.
        boolean[][] cells = circle(13);
        float[][] c = corners(Relief.SPHERE, cells, 0);
        List<Patches.Rect> rects = Patches.cover(cells, c, 0.05F);
        List<int[][]> rings = Patches.outlines(rects, N);
        Set<String> edges = new HashSet<>();
        List<String> all = new ArrayList<>();
        for (int[][] ring : rings) {
            for (int k = 0; k < ring.length; k++) {
                int[] a = ring[k], b = ring[(k + 1) % ring.length];
                String e = a[0] + "," + a[1] + ">" + b[0] + "," + b[1];
                assertTrue(edges.add(e), "drawn twice the same way: " + e);
                all.add(e);
                // Every segment runs along a grid line.
                assertTrue(Math.abs(a[0] - b[0]) == 0 || Math.abs(a[1] - b[1]) == 0, e);
            }
        }
        // Every inner segment (one with cells on both sides) is drawn once each way.
        for (String e : all) {
            String[] p = e.split(">");
            int[] a = parse(p[0]), b = parse(p[1]);
            if (inner(cells, a, b)) {
                assertTrue(edges.contains(p[1] + ">" + p[0]), "no partner for " + e);
            }
        }
    }

    private static int[] parse(String s) {
        String[] x = s.split(",");
        return new int[] {Integer.parseInt(x[0]), Integer.parseInt(x[1])};
    }

    /** effects: returns whether the axis segment a-b has a true cell on both sides */
    private static boolean inner(boolean[][] cells, int[] a, int[] b) {
        int n = cells.length;
        if (a[0] == b[0]) { // vertical: cells to its left and right
            int i = a[0], j = Math.min(a[1], b[1]);
            return i > 0 && i < n && cells[j][i - 1] && cells[j][i];
        }
        int j = a[1], i = Math.min(a[0], b[0]);
        return j > 0 && j < n && cells[j - 1][i] && cells[j][i];
    }

    @Test
    void aFanCoversEveryTriangleOfTheRing() {
        assertEquals(List.of(), Patches.fan(2));
        assertArrayEquals(new int[] {0, 1, 2, 2}, Patches.fan(3).get(0));
        assertEquals(1, Patches.fan(4).size());
        assertArrayEquals(new int[] {0, 1, 2, 3}, Patches.fan(4).get(0));
        for (int size = 3; size <= 7; size++) {
            // The game draws a quad as 0 1 2 and 2 3 0: collect those triangles, the fan's own, from 0 over k, k+1.
            Set<String> tris = new HashSet<>();
            for (int[] q : Patches.fan(size)) {
                tris.add(q[0] + " " + q[1] + " " + q[2]);
                if (q[2] != q[3]) {
                    tris.add(q[0] + " " + q[2] + " " + q[3]);
                }
            }
            Set<String> want = new HashSet<>();
            for (int k = 1; k + 1 < size; k++) {
                want.add("0 " + k + " " + (k + 1));
            }
            assertEquals(want, tris, "ring of " + size);
        }
    }

    @Test
    void aRectangleIsNeverEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new Patches.Rect(2, 0, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new Patches.Rect(0, 3, 1, 1));
        assertFalse(Patches.fits(new float[][] {{0, 0, 0}, {0, 1, 0}, {0, 0, 0}}, 0, 0, 2, 2, 0.5F));
    }
}

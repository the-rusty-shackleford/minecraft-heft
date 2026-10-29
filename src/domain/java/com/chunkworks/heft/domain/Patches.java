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

import java.util.ArrayList;
import java.util.List;

/**
 * A raised surface over a grid of cells drawn with as few quads as keep it within {@code tolerance} of its heights:
 * the cells covered by rectangles, each as level as a plane through its corners allows, and each rectangle drawn as a
 * fan through every corner of a neighbouring rectangle that lies on its edge, so the surface has no T-junctions and no
 * cracks. Every vertex is a grid corner at its own height, so normals and texture coordinates are as the cell grid's.
 *
 * <p>One quad a cell gave a raised item about 1,000 quads, 35 times an extruded one, and a gallery of them stuttered
 * (D-0011).
 */
public final class Patches {
    private Patches() {}

    /** A rectangle of cells: columns i0 until i1, rows j0 until j1. RI: i0 &lt; i1, j0 &lt; j1. */
    public record Rect(int i0, int j0, int i1, int j1) {
        public Rect {
            if (!(i0 < i1 && j0 < j1)) {
                throw new IllegalArgumentException(i0 + " " + j0 + " " + i1 + " " + j1);
            }
        }
    }

    /**
     * requires: cells is n x n; c is (n + 1) x (n + 1); tolerance &gt;= 0
     * effects: returns rectangles that cover exactly the true cells, none overlapping, chosen greedily (each grown a
     * column and a row in turn while it fits), such that every grid corner inside or on a rectangle lies within {@code tolerance}
     * of the plane-blend (bilinear) of the rectangle's four corner heights in c
     */
    public static List<Rect> cover(boolean[][] cells, float[][] c, float tolerance) {
        int n = cells.length;
        boolean[][] used = new boolean[n][n];
        List<Rect> out = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (!cells[j][i] || used[j][i]) {
                    continue;
                }
                // Grown a column and a row in turn, so it stays near square where the surface curves both ways; then
                // on in whichever direction still fits.
                int i1 = i + 1, j1 = j + 1;
                boolean wide = true, tall = true;
                while (wide || tall) {
                    if (wide) {
                        wide = i1 < n && columnFree(cells, used, j, j1, i1) && fits(c, i, j, i1 + 1, j1, tolerance);
                        if (wide) i1++;
                    }
                    if (tall) {
                        tall = j1 < n && rowFree(cells, used, i, i1, j1) && fits(c, i, j, i1, j1 + 1, tolerance);
                        if (tall) j1++;
                    }
                }
                for (int y = j; y < j1; y++) {
                    for (int x = i; x < i1; x++) {
                        used[y][x] = true;
                    }
                }
                out.add(new Rect(i, j, i1, j1));
            }
        }
        return out;
    }

    private static boolean columnFree(boolean[][] cells, boolean[][] used, int j0, int j1, int i) {
        for (int y = j0; y < j1; y++) {
            if (!cells[y][i] || used[y][i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean rowFree(boolean[][] cells, boolean[][] used, int i0, int i1, int j) {
        for (int x = i0; x < i1; x++) {
            if (!cells[j][x] || used[j][x]) {
                return false;
            }
        }
        return true;
    }

    /** effects: returns whether every corner of rectangle (i0, j0)-(i1, j1) lies within tol of the bilinear blend of its four */
    static boolean fits(float[][] c, int i0, int j0, int i1, int j1, float tol) {
        float a = c[j0][i0], b = c[j0][i1], d = c[j1][i0], e = c[j1][i1];
        for (int y = j0; y <= j1; y++) {
            float t = (float) (y - j0) / (j1 - j0);
            for (int x = i0; x <= i1; x++) {
                float s = (float) (x - i0) / (i1 - i0);
                float blend = (1 - t) * ((1 - s) * a + s * b) + t * ((1 - s) * d + s * e);
                if (Math.abs(c[y][x] - blend) > tol) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * requires: rects from {@link #cover} over an n x n grid
     * effects: returns each rectangle's outline as grid corners {i, j}, in order round it (top left, down the left
     * side, along the bottom, up the right, back along the top, as the game orders a south face), holding its four
     * corners and every corner of another rectangle that lies on its edges
     */
    public static List<int[][]> outlines(List<Rect> rects, int n) {
        boolean[][] corner = new boolean[n + 1][n + 1];
        for (Rect r : rects) {
            corner[r.j0][r.i0] = corner[r.j0][r.i1] = corner[r.j1][r.i0] = corner[r.j1][r.i1] = true;
        }
        List<int[][]> out = new ArrayList<>(rects.size());
        for (Rect r : rects) {
            List<int[]> ring = new ArrayList<>();
            for (int y = r.j0; y < r.j1; y++) { // down the left side
                if (y == r.j0 || corner[y][r.i0]) ring.add(new int[] {r.i0, y});
            }
            for (int x = r.i0; x < r.i1; x++) { // along the bottom
                if (x == r.i0 || corner[r.j1][x]) ring.add(new int[] {x, r.j1});
            }
            for (int y = r.j1; y > r.j0; y--) { // up the right side
                if (y == r.j1 || corner[y][r.i1]) ring.add(new int[] {r.i1, y});
            }
            for (int x = r.i1; x > r.i0; x--) { // back along the top
                if (x == r.i1 || corner[r.j0][x]) ring.add(new int[] {x, r.j0});
            }
            out.add(ring.toArray(new int[0][]));
        }
        return out;
    }

    /**
     * requires: ring has at least 3 corners, convex in order
     * effects: returns the ring as quads of four of its indices, a fan from its first corner, each quad two triangles
     * of the fan as the game splits a quad (0 1 2, then 2 3 0); a lone last triangle repeats its last corner
     */
    public static List<int[]> fan(int size) {
        List<int[]> out = new ArrayList<>();
        for (int k = 1; k + 1 < size; k += 2) {
            out.add(k + 2 < size ? new int[] {0, k, k + 1, k + 2} : new int[] {0, k, k + 1, k + 1});
        }
        return out;
    }
}

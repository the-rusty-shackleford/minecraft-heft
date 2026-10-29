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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions:
 * - Relief.of: an item override (over its class); a relief class; an extruded class; no class and food, by a word,
 *   by none (FOOD); no class and not food
 * - heights: symmetric relief (front = back) and not (loaf); body ROUND, DOME, CONE, FLAT; cap binding and not; edge
 *   0 and positive; a disc, a square, an empty grid; the grid's own edge as the silhouette's
 * - corners: a corner on the silhouette's edge, an inner one
 * - Side: crown out of [0, 1], a negative length
 */
class ReliefTest {
    private static final int N = 32;

    /** A filled circle of radius {@code r} cells about the grid's middle. */
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

    private static float max(float[][] h) {
        float m = 0;
        for (float[] row : h) {
            for (float v : row) {
                m = Math.max(m, v);
            }
        }
        return m;
    }

    @Test
    void anItemOverrideWinsOverItsClass() {
        assertSame(Relief.MOUND, Relief.of("farmersdelight:minced_beef", "steak", true));
        assertSame(Relief.SLAB, Relief.of("minecraft:cooked_beef", "steak", true));
    }

    @Test
    void eachClassHasTheReliefOfWhatItIs() {
        assertSame(Relief.LOAF, Relief.of("minecraft:bread", "loaf", true));
        assertSame(Relief.SPHERE, Relief.of("minecraft:apple", "fruit", true));
        assertSame(Relief.DISC, Relief.of("minecraft:cookie", "disc", true));
        assertSame(Relief.BAR, Relief.of("minecraft:iron_ingot", "ingot", false));
        assertSame(Relief.SPHERE, Relief.of("minecraft:creeper_spawn_egg", "spawn_egg", false));
    }

    @Test
    void aFlatClassIsExtruded() {
        assertNull(Relief.of("minecraft:oak_sign", "sign", false));
        assertNull(Relief.of("minecraft:music_disc_cat", "record", false));
    }

    @Test
    void aFoodWithNoClassGoesByAWordOfItsNameElseFood() {
        assertSame(Relief.FISH, Relief.of("alexsmobs:cooked_catfish", null, true));
        assertSame(Relief.LOAF, Relief.of("create:sweet_roll", null, true));
        assertSame(Relief.TUBE, Relief.of("dustydecorations:cooked_bratwurst", null, true));
        assertSame(Relief.FOOD, Relief.of("alexsmobs:gongylidia", null, true));
    }

    @Test
    void paperIsNoThickerThanTheGamesOwnFlatItem() {
        assertSame(Relief.THIN, Relief.of("minecraft:paper", "plate", false));
        assertEquals(0F, max(Relief.THIN.heights(circle(12))[0]));
    }

    @Test
    void anythingElseWithNoClassIsExtruded() {
        assertNull(Relief.of("minecraft:feather", null, false));
    }

    @Test
    void aSphereIsShallowerThanItIsWideAndTheSameBothSides() {
        boolean[][] disc = circle(12); // 12 cells is 6 sixteenths of a 32-cell grid
        float[][][] h = Relief.SPHERE.heights(disc);
        assertArrayEquals(h[0], h[1]);
        // Half the inradius at the crown (not puffed), the smoothing taking a little off.
        assertTrue(max(h[0]) > 0.4 * 6 && max(h[0]) <= 0.5 * 6, "crown " + max(h[0]));
    }

    @Test
    void aLoafHasADomedCrustOverAFlatBottom() {
        float[][][] h = Relief.LOAF.heights(circle(12));
        assertTrue(max(h[1]) <= 0.3F, "bottom " + max(h[1]));
        assertTrue(max(h[0]) > 3 * max(h[1]), "crust " + max(h[0]) + " over bottom " + max(h[1]));
        assertTrue(max(h[0]) <= 2.0F, "crust " + max(h[0]));
    }

    @Test
    void aDiscIsThinAndLevelInside() {
        boolean[][] disc = circle(14);
        float[][] h = Relief.DISC.heights(disc)[0];
        assertTrue(max(h) <= 0.35F + 1e-6, "top " + max(h));
        // Inside the rounded edge and the smoothing, level at the cap.
        assertEquals(0.35F, h[N / 2][N / 2], 1e-5);
        assertEquals(0.35F, h[N / 2][N / 2 - 6], 1e-5);
    }

    @Test
    void theCapHoldsABigSlabDownAndASmallOneRisesByItsInradius() {
        float big = max(Relief.SLAB.heights(circle(14))[0]);
        float small = max(Relief.SLAB.heights(circle(3))[0]);
        assertTrue(big <= 0.7F + 1e-6, "big " + big);
        assertTrue(small < big, "small " + small + ", big " + big);
    }

    @Test
    void everyBodyRisesFromTheEdgeToTheMiddle() {
        Relief dome = new Relief("dome", new Relief.Side(Relief.Body.DOME, 1, 0.8, Double.MAX_VALUE, 0),
                new Relief.Side(Relief.Body.DOME, 1, 0.8, Double.MAX_VALUE, 0), 2);
        for (Relief r : new Relief[] {Relief.SPHERE, Relief.GEM, dome, Relief.LOAF, Relief.MOUND, Relief.BAR}) {
            float[][] h = r.heights(circle(13))[0];
            for (int i = 3; i < N / 2; i++) {
                assertTrue(h[N / 2][i] >= h[N / 2][i - 1] - 1e-6, r + " falls at " + i);
            }
        }
    }

    @Test
    void aGemRisesStraighterThanASphere() {
        // Half way in, a cone stands at half its crown, a circle's section well over.
        float[][] gem = Relief.GEM.heights(circle(14))[0], ball = Relief.SPHERE.heights(circle(14))[0];
        int mid = N / 2, half = N / 2 - 7;
        assertTrue(gem[mid][half] / gem[mid][mid] < ball[mid][half] / ball[mid][mid]);
    }

    @Test
    void emptyCellsAndAnEmptyGridStandAtZero() {
        boolean[][] disc = circle(8);
        float[][] h = Relief.SPHERE.heights(disc)[0];
        assertEquals(0F, h[0][0]);
        assertEquals(0F, max(Relief.SPHERE.heights(new boolean[N][N])[0]));
    }

    @Test
    void theGridsOwnEdgeIsTheSilhouettes() {
        boolean[][] full = new boolean[N][N];
        for (boolean[] row : full) {
            java.util.Arrays.fill(row, true);
        }
        float[][] c = Relief.corners(full, Relief.SPHERE.heights(full)[0]);
        assertEquals(0F, c[0][N / 2]);
        assertEquals(0F, c[N][N]);
        assertTrue(c[N / 2][N / 2] > 0F);
    }

    @Test
    void aCornerOnTheEdgeIsZeroAndAnInnerOneTheMeanOfItsCells() {
        boolean[][] disc = circle(10);
        float[][] h = Relief.SPHERE.heights(disc)[0];
        float[][] c = Relief.corners(disc, h);
        int m = N / 2;
        assertEquals((h[m - 1][m - 1] + h[m - 1][m] + h[m][m - 1] + h[m][m]) / 4F, c[m][m], 1e-6);
        assertEquals(0F, c[m][m - 10]); // the corner where the circle's row meets its left edge
    }

    @Test
    void aSideOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Relief.Side(Relief.Body.ROUND, 1.5, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Relief.Side(Relief.Body.ROUND, 1, -1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Relief("x", Relief.SPHERE.front(), Relief.SPHERE.back(), -1));
    }
}

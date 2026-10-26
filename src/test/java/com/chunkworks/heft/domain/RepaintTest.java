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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions:
 * - sizeFor: 8, 16, 32 (doubled to 64); 24 (to 48); 64 and 128 (kept); not square; 0; over LARGEST; ending
 *   off a multiple of 16 (20 to 40)
 * - scale2x: one colour (unchanged); a diagonal staircase (its corners filled); an isolated texel (kept square)
 * - fill: a transparent texel beside an opaque one takes its colour, alpha 0; all transparent stays black
 * - of: the opaque mask scaled exactly, corners included (Scale2x rounds colour only); grain within its bound;
 *   deterministic by seed and differing across seeds; flat _n and dielectric _s everywhere; a bad sprite refused
 */
class RepaintTest {
    private static final int RED = 0xFFC03020, BLUE = 0xFF2040C0, CLEAR = 0x00000000;

    @Test
    void sizes() {
        assertEquals(64, Repaint.sizeFor(8, 8));
        assertEquals(64, Repaint.sizeFor(16, 16));
        assertEquals(64, Repaint.sizeFor(32, 32));
        assertEquals(48, Repaint.sizeFor(24, 24));
        assertEquals(64, Repaint.sizeFor(64, 64));
        assertEquals(128, Repaint.sizeFor(128, 128));
        assertEquals(0, Repaint.sizeFor(16, 32));
        assertEquals(0, Repaint.sizeFor(0, 0));
        assertEquals(0, Repaint.sizeFor(1024, 1024));
        assertEquals(0, Repaint.sizeFor(20, 20));
    }

    @Test
    void oneColourStaysOneColour() {
        int[] src = {RED, RED, RED, RED};
        assertArrayEquals(new int[16], new int[16]);
        for (int p : Repaint.scale2x(src, 2)) {
            assertEquals(RED, p);
        }
    }

    @Test
    void aStaircaseCornerIsFilled() {
        // R B
        // B B   -> the top-left texel's lower-right quarter turns blue: the diagonal is rounded.
        int[] src = {RED, BLUE, BLUE, BLUE};
        int[] out = Repaint.scale2x(src, 2);
        assertEquals(RED, out[0]);
        assertEquals(BLUE, out[1 * 4 + 1]);
    }

    @Test
    void anIsolatedTexelStaysSquare() {
        int[] src = new int[9];
        java.util.Arrays.fill(src, BLUE);
        src[4] = RED;
        int[] out = Repaint.scale2x(src, 3);
        assertEquals(RED, out[2 * 6 + 2]);
        assertEquals(RED, out[2 * 6 + 3]);
        assertEquals(RED, out[3 * 6 + 2]);
        assertEquals(RED, out[3 * 6 + 3]);
    }

    @Test
    void transparentTexelsTakeTheirNeighboursColour() {
        int[] out = Repaint.fill(new int[] {RED, CLEAR, CLEAR, CLEAR}, 2);
        assertEquals(RED, out[0]);
        assertEquals(RED & 0x00FFFFFF, out[1]);
        assertEquals(RED & 0x00FFFFFF, out[3]);
        assertArrayEquals(new int[4], Repaint.fill(new int[4], 2));
    }

    private static int[] square16() {
        // A 6 x 6 opaque square in a 16 x 16 sprite.
        int[] src = new int[256];
        for (int y = 5; y < 11; y++) {
            for (int x = 5; x < 11; x++) {
                src[y * 16 + x] = RED;
            }
        }
        return src;
    }

    @Test
    void theMaskScalesExactly() {
        Repaint.Sheet s = Repaint.of(square16(), 16, 16, 1L);
        assertEquals(64, s.size());
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                boolean in = x >= 20 && x < 44 && y >= 20 && y < 44;
                assertEquals(in, (s.albedo()[y * 64 + x] >>> 24) != 0, "texel " + x + "," + y);
            }
        }
    }

    @Test
    void theGrainIsSmallAndFixedBySeed() {
        Repaint.Sheet a = Repaint.of(square16(), 16, 16, 7L);
        Repaint.Sheet b = Repaint.of(square16(), 16, 16, 7L);
        Repaint.Sheet c = Repaint.of(square16(), 16, 16, 8L);
        assertArrayEquals(a.albedo(), b.albedo());
        assertTrue(!java.util.Arrays.equals(a.albedo(), c.albedo()));
        int red = (RED >> 16) & 255;
        for (int p : a.albedo()) {
            if ((p >>> 24) != 0) {
                int r = (p >> 16) & 255;
                assertTrue(Math.abs(r - red) <= red * 0.05 + 1, "red " + r);
            }
        }
    }

    @Test
    void mapsAreFlatAndDielectric() {
        Repaint.Sheet s = Repaint.of(square16(), 16, 16, 1L);
        for (int i = 0; i < 64 * 64; i++) {
            assertEquals(Repaint.FLAT_NORMAL, s.normal()[i]);
            assertEquals(Repaint.DIELECTRIC, s.specular()[i]);
        }
        assertEquals(250, Repaint.FLAT_NORMAL >>> 24);
        assertEquals(255, Repaint.DIELECTRIC >>> 24);
    }

    @Test
    void aBadSpriteIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Repaint.of(new int[200], 10, 20, 1L));
        assertThrows(IllegalArgumentException.class, () -> Repaint.of(new int[10], 16, 16, 1L));
    }
}

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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Partitions:
 * - euler: all zero; one axis (x, y, z) at 90 or -90; two axes, order mattering (x then y vs y then x);
 *   a non-finite angle
 * - apply(Box, centre): turned about the centre (extent kept, place kept); a quarter turn swapping two extents
 * - about: each axis; a bad axis
 */
class TurnTest {
    private static final double E = 1e-9;

    @Test
    void noneChangesNothing() {
        assertArrayEquals(new double[] {1, 2, 3}, Turn.NONE.apply(new double[] {1, 2, 3}), E);
    }

    @Test
    void minusNinetyAboutXTurnsTheTopOutOfTheSurface() {
        assertArrayEquals(new double[] {0, 0, -1}, Turn.euler(-90, 0, 0).apply(new double[] {0, 1, 0}), E);
    }

    @Test
    void ninetyAboutYTurnsXToMinusZ() {
        assertArrayEquals(new double[] {0, 0, -1}, Turn.euler(0, 90, 0).apply(new double[] {1, 0, 0}), E);
    }

    @Test
    void ninetyAboutZTurnsXToY() {
        assertArrayEquals(new double[] {0, 1, 0}, Turn.euler(0, 0, 90).apply(new double[] {1, 0, 0}), E);
    }

    @Test
    void zTurnsFirstThenYThenX() {
        // Rx(90) Ry(90) applied to x: Ry takes x to -z, Rx takes -z to y.
        assertArrayEquals(new double[] {0, 1, 0}, Turn.euler(90, 90, 0).apply(new double[] {1, 0, 0}), E);
    }

    @Test
    void aBoxTurnsAboutTheCentreKeepingItsPlace() {
        Box b = Turn.euler(-90, 0, 0).apply(new Box(3, 0, 5.5, 13, 3, 10.5), Shape.CENTRE);
        assertArrayEquals(new double[] {10, 5, 3}, b.size(), E);
        // Its base (y 0) goes to the back, against the surface (z 16), its top towards the viewer.
        assertArrayEquals(new double[] {3, 5.5, 13, 16}, new double[] {b.minX(), b.minY(), b.minZ(), b.maxZ()}, E);
    }

    @Test
    void aboutEachAxisMatchesEuler() {
        double[] p = {1, 2, 3};
        assertArrayEquals(Turn.euler(30, 0, 0).apply(p), Turn.about(0, 30).apply(p), E);
        assertArrayEquals(Turn.euler(0, 30, 0).apply(p), Turn.about(1, 30).apply(p), E);
        assertArrayEquals(Turn.euler(0, 0, 30).apply(p), Turn.about(2, 30).apply(p), E);
    }

    @Test
    void badInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Turn.euler(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Turn.about(3, 10));
    }
}

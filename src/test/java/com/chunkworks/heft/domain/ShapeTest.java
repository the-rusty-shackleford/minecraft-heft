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

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Partitions:
 * - Element: unturned; turned 45 without rescale (box grows by sqrt 2 across); turned 45 with rescale
 *   (stretched back along the other axes); turned about an origin off its centre; from > to; |angle| >= 90
 * - Shape.box: one element; several; turned elements counted by their real corners, not from/to
 * - Shape.rested: identity; a quarter turn
 * - Canon.breaches: none; over budget; resting thick out of the surface; both
 * - Canon.topOut: [-90, 0, 0]; identity; [90, 0, 0]
 */
class ShapeTest {
    private static final double E = 1e-9;

    private static Element box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Element.of(new double[] {x0, y0, z0}, new double[] {x1, y1, z1});
    }

    // The ingot of the canon, a plain box for these tests: 10 x 3 x 5.
    private static final Element INGOT = box(3, 0, 5.5, 13, 3, 10.5);

    @Test
    void anUnturnedElementIsItsBox() {
        assertEquals(new Box(3, 0, 5.5, 13, 3, 10.5), Box.around(INGOT.corners()));
    }

    @Test
    void fortyFiveDegreesWithoutRescaleGrowsTheBoxAcross() {
        Element e = new Element(new double[] {6, 6, 6}, new double[] {10, 10, 10}, new double[] {8, 8, 8}, 1, 45.0, false);
        double half = 2 * Math.sqrt(2);
        Box b = Box.around(e.corners());
        assertArrayEquals(new double[] {2 * half, 4, 2 * half}, b.size(), E);
    }

    @Test
    void fortyFiveDegreesWithRescaleStretchesTheOtherAxes() {
        Element e = new Element(new double[] {6, 6, 6}, new double[] {10, 10, 10}, new double[] {8, 8, 8}, 1, 45.0, true);
        Box b = Box.around(e.corners());
        assertArrayEquals(new double[] {8, 4, 8}, b.size(), E);
    }

    @Test
    void aTurnAboutAnOriginOffCentreMovesTheBox() {
        Element e = new Element(new double[] {0, 0, 0}, new double[] {2, 2, 2}, new double[] {0, 0, 0}, 2, 22.5, false);
        Box b = Box.around(e.corners());
        assertEquals(0.0, b.minY(), E);
        assertEquals(-2 * Math.sin(Math.toRadians(22.5)), b.minX(), E);
    }

    @Test
    void badElementsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> box(4, 0, 0, 3, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Element(new double[] {0, 0, 0}, new double[] {1, 1, 1}, new double[] {0, 0, 0}, 0, 90.0, false));
        assertThrows(IllegalArgumentException.class, () -> new Shape(List.of(), Turn.NONE));
    }

    @Test
    void aShapesBoxHoldsEveryElementsRealCorners() {
        Element turned = new Element(new double[] {6, 3, 6}, new double[] {10, 4, 10}, new double[] {8, 8, 8}, 1, 45.0, false);
        Shape s = new Shape(List.of(INGOT, turned), Turn.NONE);
        Box b = s.box();
        assertEquals(0.0, b.minY(), E);
        assertEquals(8 + 2 * Math.sqrt(2), b.maxZ(), E);
        assertEquals(13.0, b.maxX(), E);
    }

    @Test
    void restedIsTheBoxTurnedByFixed() {
        assertEquals(new Box(3, 0, 5.5, 13, 3, 10.5), new Shape(List.of(INGOT), Turn.NONE).rested());
        assertArrayEquals(new double[] {10, 5, 3}, new Shape(List.of(INGOT), Turn.euler(-90, 0, 0)).rested().size(), E);
    }

    @Test
    void anIngotLaidOnItsBaseKeepsTheCanon() {
        assertEquals(List.of(), Canon.breaches(new Shape(List.of(INGOT), Turn.euler(-90, 0, 0))));
    }

    @Test
    void anIngotLeftStandingOnItsSideRestsThick() {
        // Unturned, the ingot's 5-deep side faces out of the surface: thicker than its 3 of height.
        List<String> breaches = Canon.breaches(new Shape(List.of(INGOT), Turn.NONE));
        assertEquals(1, breaches.size());
        assertEquals(true, breaches.get(0).startsWith("rests 5.00 deep"));
    }

    @Test
    void overTheBudgetIsABreach() {
        List<Element> many = java.util.stream.IntStream.range(0, Canon.BUDGET + 1).mapToObj(i -> box(0, 0, 0, 16, 16, 1)).toList();
        List<String> breaches = Canon.breaches(new Shape(many, Turn.NONE));
        assertEquals(List.of((Canon.BUDGET + 1) + " elements, over the budget of " + Canon.BUDGET), breaches);
    }

    @Test
    void overBudgetAndThickAreBothReported() {
        List<Element> many = java.util.stream.IntStream.range(0, Canon.BUDGET + 1).mapToObj(i -> INGOT).toList();
        assertEquals(2, Canon.breaches(new Shape(many, Turn.NONE)).size());
    }

    @Test
    void topOutIsMinusNinetyAboutX() {
        assertEquals(true, Canon.topOut(Turn.euler(-90, 0, 0)));
        assertEquals(false, Canon.topOut(Turn.NONE));
        assertEquals(false, Canon.topOut(Turn.euler(90, 0, 0)));
    }
}

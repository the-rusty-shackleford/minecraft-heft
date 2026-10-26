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
 * What every solid item's shape must keep to (D-0003): few enough elements for a grid of hundreds of
 * them in a recipe viewer, and an item-frame turn that lays it with its thinnest extent out of the
 * surface and its own top towards the viewer, as Set It Down and an item frame both show it.
 */
public final class Canon {
    private Canon() {}

    /** The most elements a solid item's model may have. */
    public static final int BUDGET = 40;

    /** How much longer than the least an extent may be and still tie with it, sixteenths: a measuring hair. */
    public static final double TIE = 1e-4;

    /** Out of the surface, in the frame an item frame and Set It Down show a model in: towards the viewer. */
    private static final double[] OUT = {0.0, 0.0, -1.0};

    /**
     * effects: returns what {@code shape} breaks of the canon, one line each, empty if nothing: more
     * elements than {@link #BUDGET}; shown thicker out of the surface than across it
     */
    public static List<String> breaches(Shape shape) {
        List<String> out = new ArrayList<>();
        if (shape.elements().size() > BUDGET) {
            out.add(shape.elements().size() + " elements, over the budget of " + BUDGET);
        }
        double[] s = shape.rested().size();
        if (s[2] > Math.min(s[0], s[1]) + TIE) {
            out.add("rests " + fmt(s[2]) + " deep out of the surface, thicker than across it (" + fmt(s[0]) + " x " + fmt(s[1]) + ")");
        }
        return out;
    }

    /** effects: returns whether {@code fixed} turns a model's own top (+y) out of the surface */
    public static boolean topOut(Turn fixed) {
        double[] up = fixed.apply(new double[] {0.0, 1.0, 0.0});
        return Math.abs(up[0] - OUT[0]) < 1e-9 && Math.abs(up[1] - OUT[1]) < 1e-9 && Math.abs(up[2] - OUT[2]) < 1e-9;
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}

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
 * A solid item's model as far as its shape goes: its elements, and how its item-frame transform
 * turns it -- which is also how Set It Down lays it down, since a model the hand draws as the item's
 * own is posed set down by its own {@code fixed} rotation (Set It Down's {@code HeldView}).
 *
 * <p>AF: the union of {@code elements}, shown in an item frame or set down turned by {@code fixed}
 * about the block's centre (8, 8, 8).
 * RI: elements not empty.
 */
public record Shape(List<Element> elements, Turn fixed) {
    /** The block's centre, which every display transform turns about. */
    public static final double[] CENTRE = {8.0, 8.0, 8.0};

    public Shape {
        elements = List.copyOf(elements);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("a shape with no elements");
        }
    }

    /** effects: returns the least box holding every element where the game draws it, in the model's own space */
    public Box box() {
        return Box.around(corners(Turn.NONE));
    }

    /** effects: returns the least box holding every element turned as an item frame or Set It Down shows it */
    public Box rested() {
        return Box.around(corners(fixed));
    }

    private double[][] corners(Turn turn) {
        List<double[]> out = new ArrayList<>();
        for (Element e : elements) {
            for (double[] p : e.corners()) {
                double[] q = turn.apply(new double[] {p[0] - CENTRE[0], p[1] - CENTRE[1], p[2] - CENTRE[2]});
                out.add(new double[] {q[0] + CENTRE[0], q[1] + CENTRE[1], q[2] + CENTRE[2]});
            }
        }
        return out.toArray(double[][]::new);
    }
}

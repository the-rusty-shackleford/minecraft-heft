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

/**
 * One box of a model, as a model file's element is: from one corner to the other, perhaps turned
 * about one axis through an origin, perhaps stretched back to its span after the turn.
 *
 * <p>AF: the points of box(from, to) turned by {@code angle} degrees about the line through
 * {@code origin} along {@code axis}, then, if {@code rescale}, stretched from the origin by
 * 1/cos(angle) along the two other axes, as the game bakes it.
 * RI: from and to and origin of length 3, finite, from <= to on each axis; axis 0, 1 or 2;
 * |angle| < 90.
 */
public record Element(double[] from, double[] to, double[] origin, int axis, double angle, boolean rescale) {
    public Element {
        from = from.clone();
        to = to.clone();
        origin = origin.clone();
        if (from.length != 3 || to.length != 3 || origin.length != 3 || axis < 0 || axis > 2 || !(Math.abs(angle) < 90.0)) {
            throw new IllegalArgumentException("an element");
        }
        new Box(from[0], from[1], from[2], to[0], to[1], to[2]);
        if (!Double.isFinite(origin[0] + origin[1] + origin[2])) {
            throw new IllegalArgumentException("an element's origin");
        }
    }

    /** effects: returns the element from {@code from} to {@code to}, unturned */
    public static Element of(double[] from, double[] to) {
        return new Element(from, to, new double[] {8, 8, 8}, 1, 0.0, false);
    }

    @Override
    public double[] from() {
        return from.clone();
    }

    @Override
    public double[] to() {
        return to.clone();
    }

    @Override
    public double[] origin() {
        return origin.clone();
    }

    /** effects: returns its eight corners where the game draws them, turned and stretched */
    public double[][] corners() {
        double[][] corners = new Box(from[0], from[1], from[2], to[0], to[1], to[2]).corners();
        if (angle == 0.0) {
            return corners;
        }
        Turn turn = Turn.about(axis, angle);
        double stretch = rescale ? 1.0 / Math.cos(Math.toRadians(angle)) : 1.0;
        for (int i = 0; i < corners.length; i++) {
            double[] p = corners[i];
            double[] q = turn.apply(new double[] {p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]});
            for (int k = 0; k < 3; k++) {
                q[k] = (k == axis ? q[k] : q[k] * stretch) + origin[k];
            }
            corners[i] = q;
        }
        return corners;
    }
}

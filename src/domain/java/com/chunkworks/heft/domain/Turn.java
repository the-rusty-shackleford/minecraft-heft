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
 * A turn of a model's space, as a display transform's rotation turns it: degrees about x, then y,
 * then z of the turned frame (the game's {@code Quaternionf.rotationXYZ}, so a point p goes to
 * Rx(Ry(Rz p))).
 *
 * <p>AF: the rotation whose matrix is {@code m}, row-major.
 * RI: m has nine finite entries and is orthonormal with determinant 1 (to rounding).
 */
public final class Turn {
    /** The turn that changes nothing. */
    public static final Turn NONE = euler(0.0, 0.0, 0.0);

    private final double[] m;

    private Turn(double[] m) {
        this.m = m;
    }

    /**
     * requires: x, y, z finite
     * effects: returns the turn a display transform with rotation [x, y, z] (degrees) makes
     */
    public static Turn euler(double x, double y, double z) {
        if (!Double.isFinite(x + y + z)) {
            throw new IllegalArgumentException("a turn: " + x + "," + y + "," + z);
        }
        double[] rx = matrix(0, x), ry = matrix(1, y), rz = matrix(2, z);
        return new Turn(times(rx, times(ry, rz)));
    }

    /**
     * requires: axis is 0 (x), 1 (y) or 2 (z); degrees finite
     * effects: returns the turn of {@code degrees} about {@code axis}, counter-clockwise looking down it
     */
    public static Turn about(int axis, double degrees) {
        return new Turn(matrix(axis, degrees));
    }

    /** effects: returns the image of point {@code p} (length 3) under this turn, about the origin */
    public double[] apply(double[] p) {
        return new double[] {
                m[0] * p[0] + m[1] * p[1] + m[2] * p[2],
                m[3] * p[0] + m[4] * p[1] + m[5] * p[2],
                m[6] * p[0] + m[7] * p[1] + m[8] * p[2]};
    }

    /**
     * requires: {@code centre} of length 3
     * effects: returns the least box holding the image of {@code box} turned about {@code centre}
     */
    public Box apply(Box box, double[] centre) {
        double[][] corners = box.corners();
        for (int i = 0; i < corners.length; i++) {
            double[] p = corners[i];
            double[] q = apply(new double[] {p[0] - centre[0], p[1] - centre[1], p[2] - centre[2]});
            corners[i] = new double[] {q[0] + centre[0], q[1] + centre[1], q[2] + centre[2]};
        }
        return Box.around(corners);
    }

    private static double[] matrix(int axis, double degrees) {
        if (axis < 0 || axis > 2 || !Double.isFinite(degrees)) {
            throw new IllegalArgumentException("a turn about " + axis + " by " + degrees);
        }
        double r = Math.toRadians(degrees), c = Math.cos(r), s = Math.sin(r);
        return switch (axis) {
            case 0 -> new double[] {1, 0, 0, 0, c, -s, 0, s, c};
            case 1 -> new double[] {c, 0, s, 0, 1, 0, -s, 0, c};
            default -> new double[] {c, -s, 0, s, c, 0, 0, 0, 1};
        };
    }

    private static double[] times(double[] a, double[] b) {
        double[] out = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        return out;
    }
}

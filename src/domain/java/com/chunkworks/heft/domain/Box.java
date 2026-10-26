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
 * An axis-aligned box in a model's own space, sixteen units to the block.
 *
 * <p>AF: every point (x, y, z) with minX <= x <= maxX, minY <= y <= maxY, minZ <= z <= maxZ.
 * RI: each min is at most its max; all six are finite.
 */
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    public Box {
        if (!(minX <= maxX && minY <= maxY && minZ <= maxZ) || !Double.isFinite(minX + minY + minZ + maxX + maxY + maxZ)) {
            throw new IllegalArgumentException("a box: " + minX + "," + minY + "," + minZ + " to " + maxX + "," + maxY + "," + maxZ);
        }
    }

    /**
     * requires: {@code points} is not empty, each of length 3 and finite
     * effects: returns the least box holding every one of {@code points}
     */
    public static Box around(double[]... points) {
        if (points.length == 0) {
            throw new IllegalArgumentException("no points");
        }
        double x0 = Double.POSITIVE_INFINITY, y0 = x0, z0 = x0;
        double x1 = Double.NEGATIVE_INFINITY, y1 = x1, z1 = x1;
        for (double[] p : points) {
            x0 = Math.min(x0, p[0]); y0 = Math.min(y0, p[1]); z0 = Math.min(z0, p[2]);
            x1 = Math.max(x1, p[0]); y1 = Math.max(y1, p[1]); z1 = Math.max(z1, p[2]);
        }
        return new Box(x0, y0, z0, x1, y1, z1);
    }

    /** effects: returns the least box holding this one and {@code other} */
    public Box union(Box other) {
        return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }

    /** effects: returns its eight corners, the low corner first */
    public double[][] corners() {
        double[][] out = new double[8][];
        for (int c = 0; c < 8; c++) {
            out[c] = new double[] {(c & 1) == 0 ? minX : maxX, (c & 2) == 0 ? minY : maxY, (c & 4) == 0 ? minZ : maxZ};
        }
        return out;
    }

    /** effects: returns its extent along x, y and z: {width, height, depth} */
    public double[] size() {
        return new double[] {maxX - minX, maxY - minY, maxZ - minZ};
    }
}

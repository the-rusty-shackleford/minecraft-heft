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
 * The long tail's art (Tier B, D-0004): a mod's own flat sprite repainted at 64 texels to the block for
 * its extruded model. Computed on the client from the sprite in the owning mod's jar, never shipped
 * (D-0001).
 *
 * <ul>
 *   <li>Colour: doubled by Scale2x (EPX) until it is 64 across, which rounds a pixel-art staircase into
 *   a diagonal without blurring a single edge; a sprite 64 or more across is kept as it is. The mask
 *   (alpha) is scaled exactly, texel for texel: the extruded model's edges stand on the sprite's own
 *   pixels, so a rounded corner would leave an edge with no face. A fine grain
 *   of our own is laid over the opaque texels, a few hundredths of value, broad across a source pixel
 *   and finer within it. Transparent texels keep the colour of their nearest opaque one, so filtering
 *   and mipmaps never pull black into an edge (the handoff's §5.1 rule 5).</li>
 *   <li>Maps: art we did not author gets flat normals and height (the handoff's §5.1 rule 2): the face
 *   level everywhere, no occlusion; a dielectric's smoothness and F0, no emission.</li>
 * </ul>
 *
 * <p>Pixels are ARGB ints, row-major, the top row first.
 */
public final class Repaint {
    private Repaint() {}

    /** Texels across a repainted sheet: 64 to the block (D-0004). */
    public static final int TARGET = 64;
    /** The largest sprite repainted; a bigger one is left flat. */
    public static final int LARGEST = 512;
    /** LabPBR _n texel for a flat face: normal straight out (128, 128), no occlusion (255), height 250. */
    public static final int FLAT_NORMAL = (250 << 24) | (128 << 16) | (128 << 8) | 255;
    /** LabPBR _s texel: smoothness 77 (0.3), F0 10 (a dielectric), porosity 0, no emission (255). */
    public static final int DIELECTRIC = (255 << 24) | (77 << 16) | (10 << 8);
    private static final double BROAD = 0.03;
    private static final double FINE = 0.015;

    /**
     * A repainted sprite.
     *
     * <p>AF: a size x size sheet whose texel (x, y) has colour albedo[y * size + x] and maps normal[...]
     * and specular[...]. RI: all three arrays have size * size entries; size is a multiple of 16.
     */
    public record Sheet(int size, int[] albedo, int[] normal, int[] specular) {}

    /** effects: returns the side of the sheet a w x h sprite repaints to, or 0 if it is not repainted
     * (not square, empty, too big, or not ending a multiple of 16 across, which would cost the atlas its mipmaps) */
    public static int sizeFor(int w, int h) {
        if (w != h || w <= 0 || w > LARGEST) {
            return 0;
        }
        int s = w;
        while (s * 2 <= TARGET) {
            s *= 2;
        }
        return s % 16 == 0 ? s : 0;
    }

    /**
     * requires: argb has w * h entries; sizeFor(w, h) > 0
     * effects: returns the sprite repainted, its grain drawn from seed (the same seed, the same sheet)
     */
    public static Sheet of(int[] argb, int w, int h, long seed) {
        int size = sizeFor(w, h);
        if (size == 0 || argb.length != w * h) {
            throw new IllegalArgumentException("not a repaintable sprite: " + w + " x " + h);
        }
        // The colour, filled out under the transparent texels, is doubled as if opaque everywhere; the mask is
        // scaled exactly, texel for texel, since the model's edges stand on the sprite's own pixels.
        int[] colour = fill(clearTransparent(argb), w);
        for (int i = 0; i < colour.length; i++) {
            colour[i] |= 0xFF000000;
        }
        int s = w;
        while (s < size) {
            colour = scale2x(colour, s);
            s *= 2;
        }
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int alpha = argb[(y * h / size) * w + x * w / size] >>> 24;
                px[y * size + x] = (alpha << 24) | (colour[y * size + x] & 0x00FFFFFF);
            }
        }
        int cell = Math.max(1, size / w);
        int[] normal = new int[size * size];
        int[] specular = new int[size * size];
        java.util.Arrays.fill(normal, FLAT_NORMAL);
        java.util.Arrays.fill(specular, DIELECTRIC);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if ((px[i] >>> 24) == 0) {
                    continue;
                }
                double g = 1.0 + BROAD * smooth(seed, x, y, cell) + FINE * hash(seed ^ 0x5DEECE66DL, x, y);
                px[i] = scaled(px[i], g);
            }
        }
        return new Sheet(size, px, normal, specular);
    }

    /** effects: returns src (n x n) doubled by Scale2x: each texel's quarter takes a neighbour's colour where
     * two neighbours meeting at that corner agree and the other two do not, else its own */
    static int[] scale2x(int[] src, int n) {
        int m = n * 2;
        int[] out = new int[m * m];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = src[y * n + x];
                int a = src[Math.max(y - 1, 0) * n + x];
                int b = src[y * n + Math.min(x + 1, n - 1)];
                int c = src[y * n + Math.max(x - 1, 0)];
                int d = src[Math.min(y + 1, n - 1) * n + x];
                int e0 = c == a && c != d && a != b ? a : p;
                int e1 = a == b && a != c && b != d ? b : p;
                int e2 = d == c && d != b && c != a ? c : p;
                int e3 = b == d && b != a && d != c ? d : p;
                out[(2 * y) * m + 2 * x] = e0;
                out[(2 * y) * m + 2 * x + 1] = e1;
                out[(2 * y + 1) * m + 2 * x] = e2;
                out[(2 * y + 1) * m + 2 * x + 1] = e3;
            }
        }
        return out;
    }

    /** effects: returns argb with every fully transparent texel made 0, so all of them compare equal */
    static int[] clearTransparent(int[] argb) {
        int[] out = argb.clone();
        for (int i = 0; i < out.length; i++) {
            if ((out[i] >>> 24) == 0) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** effects: returns px (n x n) with each transparent texel given the colour (alpha kept 0) of an opaque texel
     * nearest it in steps, found outward from the opaque ones; all transparent if none is opaque */
    static int[] fill(int[] px, int n) {
        int[] out = px.clone();
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        boolean[] done = new boolean[px.length];
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) != 0) {
                done[i] = true;
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % n, y = i / n;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] st : steps) {
                int nx = x + st[0], ny = y + st[1];
                if (nx < 0 || ny < 0 || nx >= n || ny >= n) {
                    continue;
                }
                int j = ny * n + nx;
                if (!done[j]) {
                    done[j] = true;
                    out[j] = out[i] & 0x00FFFFFF;
                    queue.add(j);
                }
            }
        }
        return out;
    }

    private static int scaled(int argb, double g) {
        int r = clamp((int) Math.round(((argb >> 16) & 255) * g));
        int gr = clamp((int) Math.round(((argb >> 8) & 255) * g));
        int b = clamp((int) Math.round((argb & 255) * g));
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** effects: returns value noise in -1..1 at texel (x, y), lattice points `cell` texels apart, smoothly interpolated */
    private static double smooth(long seed, int x, int y, int cell) {
        double fx = (x + 0.5) / cell - 0.5, fy = (y + 0.5) / cell - 0.5;
        int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
        double tx = fade(fx - x0), ty = fade(fy - y0);
        double a = hash(seed, x0, y0), b = hash(seed, x0 + 1, y0), c = hash(seed, x0, y0 + 1), d = hash(seed, x0 + 1, y0 + 1);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * ty;
    }

    private static double fade(double t) {
        return t * t * (3 - 2 * t);
    }

    /** effects: returns a value in -1..1 fixed by (seed, x, y) */
    private static double hash(long seed, int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return ((h >>> 11) / (double) (1L << 53)) * 2.0 - 1.0;
    }
}

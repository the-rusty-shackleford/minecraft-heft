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

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a sprite's silhouette is raised into a body (D-0010): what the thing is decides its section. A fruit is round;
 * a loaf has a flat bottom, a domed crust and squared ends; a cookie is a thin disc; a steak is a slab.
 *
 * <p>Each side (the front, where the sprite faces the viewer, and the back, which a set-down item rests on) rises
 * from the silhouette's edge by its distance d from it. Its height is {@code H * shoulder(d) * body(t)}: H is
 * {@code min(rel * r, cap)}, r the silhouette's inradius; the shoulder rounds the first {@code edge} sixteenths up to
 * full height on a parabola (squared ends; none if edge is 0); the body is the section across the whole width, t = d / r, weighted
 * by {@code crown} (0: flat on top, 1: the body alone).
 *
 * <p>All lengths are sixteenths of the sprite's square, at any resolution. AF: the body whose front and back
 * stand {@link #heights} over a rim through the sprite's plane. RI: smoothing &gt;= 0.
 */
public record Relief(String name, Side front, Side back, int smoothing) {
    public enum Body {
        /** A circle's section: steep at the edge, flat on top (a fruit, a tube). */
        ROUND,
        /** A parabola's: gentler at the edge. */
        DOME,
        /** Straight to a ridge: a gem's facets, a heap. */
        CONE,
        /** Level: the shoulder alone. */
        FLAT
    }

    /** RI: crown in [0, 1]; rel, cap, edge &gt;= 0. */
    public record Side(Body body, double crown, double rel, double cap, double edge) {
        public Side {
            if (!(crown >= 0.0 && crown <= 1.0) || !(rel >= 0.0) || !(cap >= 0.0) || !(edge >= 0.0)) {
                throw new IllegalArgumentException(body + " " + crown + " " + rel + " " + cap + " " + edge);
            }
        }

        /** effects: returns this side's height in sixteenths at distance d from the edge, of a silhouette of inradius r (both sixteenths) */
        double at(double d, double r) {
            if (d <= 0.0 || r <= 0.0) {
                return 0.0;
            }
            double t = Math.min(d / r, 1.0);
            double shape = switch (body) {
                case ROUND -> circle(t);
                case DOME -> 1.0 - (1.0 - t) * (1.0 - t);
                case CONE -> t;
                case FLAT -> 1.0;
            };
            // A parabola, not a circle, at the edge: a circle stands vertical there and saw-teeths a pixel-stepped outline.
            double e = Math.min(d / edge, 1.0);
            double shoulder = edge > 0.0 ? 1.0 - (1.0 - e) * (1.0 - e) : 1.0;
            return Math.min(rel * r, cap) * shoulder * ((1.0 - crown) + crown * shape);
        }

        private static double circle(double x) {
            return Math.sqrt(Math.max(0.0, 1.0 - (1.0 - x) * (1.0 - x)));
        }
    }

    public Relief {
        if (smoothing < 0) {
            throw new IllegalArgumentException("smoothing " + smoothing);
        }
    }

    private static final double ANY = Double.MAX_VALUE;

    private static Relief same(String name, Side side) {
        return new Relief(name, side, side, 1);
    }

    // Not puffy (D-0010): every body stands lower than its round shape would, its top fuller (crown under 1), its edge
    // crisper (one smoothing pass): solid things, not stuffed ones.

    /** Round, though shallower than it is wide: a fruit, a berry, an egg, an orb. */
    public static final Relief SPHERE = same("sphere", new Side(Body.ROUND, 0.8, 0.5, ANY, 0));
    /** A loaf: a low domed crust over squared ends, a flat bottom. */
    public static final Relief LOAF = new Relief("loaf", new Side(Body.ROUND, 0.4, 0.5, 2.0, 1.25), new Side(Body.FLAT, 0, 0.2, 0.3, 0.5), 1);
    /** A cookie: a thin disc with rounded edges. */
    public static final Relief DISC = same("disc", new Side(Body.FLAT, 0, 1, 0.35, 0.4));
    /** A steak, a chop: a slab, barely fuller in the middle. */
    public static final Relief SLAB = same("slab", new Side(Body.ROUND, 0.2, 0.4, 0.7, 0.75));
    /** A melon slice: a level slab of rind and flesh. */
    public static final Relief WEDGE = same("wedge", new Side(Body.FLAT, 0, 0.4, 0.8, 0.75));
    /** A pie (or its slice): a low, flat-topped crust on a flat bottom. */
    public static final Relief PIE = new Relief("pie", new Side(Body.ROUND, 0.2, 0.4, 1.0, 0.75), new Side(Body.FLAT, 0, 0.2, 0.3, 0.5), 1);
    /** A dish on a plate, a bowl of something: low on a flat bottom. */
    public static final Relief PLATED = new Relief("plated", new Side(Body.ROUND, 0.4, 0.4, 1.25, 0.75), new Side(Body.FLAT, 0, 0.2, 0.3, 0.5), 1);
    /** A heap of powder: a low cone on a flat bottom. */
    public static final Relief MOUND = new Relief("mound", new Side(Body.CONE, 1, 0.35, 1.5, 0), new Side(Body.FLAT, 0, 0.2, 0.2, 0.4), 1);
    /** A rough lump: coal, raw ore, a nugget. */
    public static final Relief LUMP = same("lump", new Side(Body.ROUND, 0.6, 0.35, ANY, 0));
    /** A potato: a little rounder than a lump. */
    public static final Relief TUBER = same("tuber", new Side(Body.ROUND, 0.7, 0.45, ANY, 0));
    /** Round across its length: a carrot, a leg, a drumstick, a bone, a sausage. */
    public static final Relief TUBE = same("tube", new Side(Body.ROUND, 0.8, 0.5, ANY, 0));
    /** Cut facets to a ridge: a gem, a shard. */
    public static final Relief GEM = same("gem", new Side(Body.CONE, 1, 0.35, ANY, 0));
    /** A fish: rounded, much flatter than it is deep. */
    public static final Relief FISH = same("fish", new Side(Body.ROUND, 0.6, 0.3, ANY, 0));
    /** A bottle: round, shallower than a fruit. */
    public static final Relief BOTTLE = same("bottle", new Side(Body.ROUND, 0.7, 0.4, ANY, 0));
    /** A shell: domed over a flat underside. */
    public static final Relief SHELL = new Relief("shell", new Side(Body.ROUND, 0.8, 0.45, ANY, 0), new Side(Body.FLAT, 0, 0.2, 0.3, 0.5), 1);
    /** An ingot, a brick: a level bar with bevelled edges. */
    public static final Relief BAR = same("bar", new Side(Body.FLAT, 0, 1, 1.0, 0.6));
    /** A sandwich: a level stack. */
    public static final Relief STACK = same("stack", new Side(Body.FLAT, 0, 1, 1.0, 0.75));
    /** Paper, a hide, a sherd: no thicker than the game's own flat item, its outline smoothed. */
    public static final Relief THIN = same("thin", new Side(Body.FLAT, 0, 0, 0, 0));
    /** A food nothing more is known of: rounded, not puffed. */
    public static final Relief FOOD = same("food", new Side(Body.ROUND, 0.6, 0.35, ANY, 0));

    /** Heft's classes (assets/heft/classes.json) made solid by a relief; any other class is extruded. */
    private static final Map<String, Relief> BY_CLASS = Map.ofEntries(
            Map.entry("fruit", SPHERE), Map.entry("berries", SPHERE), Map.entry("egg", SPHERE), Map.entry("orb", SPHERE),
            Map.entry("spawn_egg", SPHERE),
            Map.entry("loaf", LOAF), Map.entry("disc", DISC), Map.entry("steak", SLAB), Map.entry("chop", SLAB),
            Map.entry("wedge", WEDGE), Map.entry("pie", PIE), Map.entry("pie_slice", PIE), Map.entry("plated", PLATED),
            Map.entry("dust", MOUND), Map.entry("lump", LUMP), Map.entry("raw", LUMP), Map.entry("nugget", LUMP),
            Map.entry("tuber", TUBER), Map.entry("root", TUBE), Map.entry("leg", TUBE), Map.entry("drumstick", TUBE),
            Map.entry("bone", TUBE), Map.entry("gem", GEM), Map.entry("shard", GEM), Map.entry("fish", FISH),
            Map.entry("bowl", PLATED), Map.entry("shell", SHELL), Map.entry("ingot", BAR), Map.entry("bar", BAR),
            Map.entry("stack", STACK), Map.entry("plate", THIN));

    /** Items whose class is too broad for them. */
    private static final Map<String, Relief> BY_ITEM = Map.of(
            "farmersdelight:minced_beef", MOUND,
            "minecraft:beetroot", TUBER,
            "minecraft:fermented_spider_eye", LUMP,
            "minecraft:firework_star", LUMP);

    /** A food with no class, by a word of its name; the first that matches. */
    private static final List<Map.Entry<List<String>, Relief>> BY_WORD = List.of(
            Map.entry(List.of("cookie"), DISC),
            Map.entry(List.of("fish", "cod", "salmon", "koi"), FISH),
            Map.entry(List.of("egg"), SPHERE),
            Map.entry(List.of("bread", "loaf", "roll", "bun", "burger", "dough"), LOAF),
            Map.entry(List.of("bratwurst", "sausage", "banana", "tail", "maggot", "wrap", "shank", "stick"), TUBE),
            Map.entry(List.of("meat", "ribs", "steak", "chop", "pasta", "bar_of"), SLAB),
            Map.entry(List.of("stew", "soup", "rice", "sopa", "salad"), PLATED),
            Map.entry(List.of("cider", "tea", "oil", "repellent", "juice", "bottle", "milk"), BOTTLE),
            Map.entry(List.of("jelly", "berries", "berry", "apple", "peach", "acorn", "heart", "fruit"), SPHERE));

    /**
     * effects: returns the relief item {@code id} ("namespace:path") of Heft class {@code klass} (null: none) is made
     * solid with, or null if it is extruded: by the item where its class is too broad, else by its class; a food with
     * no class by a word of its name, else {@link #FOOD}; anything else with no class is extruded
     */
    public static Relief of(String id, String klass, boolean food) {
        Relief own = BY_ITEM.get(id);
        if (own != null) {
            return own;
        }
        if (klass != null) {
            return BY_CLASS.get(klass);
        }
        if (!food) {
            return null;
        }
        String path = id.substring(id.indexOf(':') + 1).toLowerCase(Locale.ROOT);
        for (Map.Entry<List<String>, Relief> e : BY_WORD) {
            for (String word : e.getKey()) {
                if (path.contains(word)) {
                    return e.getValue();
                }
            }
        }
        return FOOD;
    }

    /**
     * requires: solid is n x n, n &gt; 0
     * effects: returns {front, back}: each solid cell's height over the rim, in sixteenths, by its distance from the
     * nearest empty cell or the grid's edge; 0 for every empty cell
     */
    public float[][][] heights(boolean[][] solid) {
        int n = solid.length;
        double cell = 16.0 / n;
        float[][] d = distances(solid);
        double r = 0;
        for (float[] row : d) {
            for (float v : row) {
                r = Math.max(r, v);
            }
        }
        float[][] f = new float[n][n], b = new float[n][n];
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (solid[j][i]) {
                    f[j][i] = (float) front.at(d[j][i] * cell, r * cell);
                    b[j][i] = (float) back.at(d[j][i] * cell, r * cell);
                }
            }
        }
        return new float[][][] {smooth(solid, f), smooth(solid, b)};
    }

    /**
     * effects: returns each solid cell's distance to the nearest empty cell or the grid's edge, in cells, measured to
     * the cell's middle (a two-pass chamfer, 1 along and root 2 across, less a half); 0 for an empty cell
     */
    static float[][] distances(boolean[][] solid) {
        int n = solid.length;
        float[][] d = new float[n][n];
        final float r2 = 1.4142F;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (!solid[j][i]) {
                    continue;
                }
                float best = Math.min(i + 1F, j + 1F);
                if (i > 0) best = Math.min(best, d[j][i - 1] + 1F);
                if (j > 0) best = Math.min(best, d[j - 1][i] + 1F);
                if (i > 0 && j > 0) best = Math.min(best, d[j - 1][i - 1] + r2);
                if (i < n - 1 && j > 0) best = Math.min(best, d[j - 1][i + 1] + r2);
                d[j][i] = best;
            }
        }
        for (int j = n - 1; j >= 0; j--) {
            for (int i = n - 1; i >= 0; i--) {
                if (!solid[j][i]) {
                    continue;
                }
                float best = Math.min(d[j][i], Math.min(n - i, n - j));
                if (i < n - 1) best = Math.min(best, d[j][i + 1] + 1F);
                if (j < n - 1) best = Math.min(best, d[j + 1][i] + 1F);
                if (i < n - 1 && j < n - 1) best = Math.min(best, d[j + 1][i + 1] + r2);
                if (i > 0 && j < n - 1) best = Math.min(best, d[j + 1][i - 1] + r2);
                d[j][i] = best;
            }
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (solid[j][i]) {
                    d[j][i] -= 0.5F;
                }
            }
        }
        return d;
    }

    /** effects: returns h after {@code smoothing} passes of a 3 x 3 mean over the solid cells (an empty one counts 0), so a pixel-stepped edge eases out */
    private float[][] smooth(boolean[][] solid, float[][] h) {
        int n = solid.length;
        for (int pass = 0; pass < smoothing; pass++) {
            float[][] out = new float[n][n];
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    if (!solid[j][i]) {
                        continue;
                    }
                    float sum = 0;
                    for (int y = j - 1; y <= j + 1; y++) {
                        for (int x = i - 1; x <= i + 1; x++) {
                            if (x >= 0 && y >= 0 && x < n && y < n) {
                                sum += h[y][x];
                            }
                        }
                    }
                    out[j][i] = sum / 9F;
                }
            }
            h = out;
        }
        return h;
    }

    /**
     * requires: h is n x n, as solid
     * effects: returns the height at each of the (n + 1) x (n + 1) grid corners: 0 where a corner touches an empty cell
     * or the grid's edge, else the mean of its four cells
     */
    public static float[][] corners(boolean[][] solid, float[][] h) {
        int n = solid.length;
        float[][] c = new float[n + 1][n + 1];
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                float sum = 0;
                boolean edge = false;
                for (int y = j - 1; y <= j; y++) {
                    for (int x = i - 1; x <= i; x++) {
                        if (x < 0 || y < 0 || x >= n || y >= n || !solid[y][x]) {
                            edge = true;
                        } else {
                            sum += h[y][x];
                        }
                    }
                }
                c[j][i] = edge ? 0F : sum / 4F;
            }
        }
        return c;
    }

    @Override
    public String toString() {
        return name;
    }
}

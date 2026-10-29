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
package com.chunkworks.heft.client;

import com.chunkworks.heft.domain.Patches;
import com.chunkworks.heft.domain.Relief;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A flat item drawn solid from the sprite the game already draws it with: the resource pack's where one replaces
 * it, at its own resolution, on the game's own atlas, so a pack's LabPBR maps come with it (D-0009).
 *
 * <ul>
 * <li>Extruded (no relief): the game's own flat model (a front, a back and an edge along every run of pixels),
 * made {@code depth} sixteenths thick.</li>
 * <li>Raised by a {@link Relief} (D-0010): the silhouette made a body of what the thing is (a fruit round, a loaf
 * domed over a flat bottom, a cookie a thin disc), the sprite laid over front and back: each cell of a grid over the
 * sprite raised by its distance from the silhouette's edge, front and back each by its own section, a thin rim where
 * they meet.</li>
 * </ul>
 *
 * <p>Drawn at its flat original's size and posed by its transforms, as the game draws the item: relative sizes are
 * vanilla's (D-0010).
 *
 * <p>Raised in as many quads as keep each side within {@link #TOLERANCE} of its heights ({@link Patches}), and at three
 * levels of detail, chosen at each draw by how far the item is from the camera ({@link HeftClient#detail}): its grid
 * of {@link #GRID} cells, then half, then a quarter (D-0011).
 *
 * <p>AF: original, drawn with lods[detail], posed per view by original's transforms. RI: lods has 3 lists, each
 * non-empty.
 */
public final class LiveModel implements BakedModel {
    /**
     * effects: returns the relief {@code item} ({@code id}) of Heft class {@code klass} (null if it has none) is raised by,
     * or null if it is extruded: by what it is (Relief.of)
     */
    @Nullable
    public static Relief relief(net.minecraft.world.item.Item item, net.minecraft.resources.ResourceLocation id, @Nullable String klass) {
        return Relief.of(id.toString(), klass, item.components().has(net.minecraft.core.component.DataComponents.FOOD));
    }

    private static final int STRIDE = 8; // DefaultVertexFormat.BLOCK, in ints: x y z, colour, u v, light, normal
    /** Cells across the raised grid up close; each further level of detail has half as many. */
    static final int GRID = Integer.getInteger("heft.grid", 32);
    /** Levels of detail: the full grid, half, a quarter. */
    public static final int DETAILS = 3;
    /** Extruded thickness in sixteenths; the game's own is one. */
    static final float DEPTH = Float.parseFloat(System.getProperty("heft.depth", "2"));
    /** How far, in sixteenths, a raised surface may stray from its heights where its cells are drawn as one quad. */
    static final float TOLERANCE = Float.parseFloat(System.getProperty("heft.tolerance", "0.1"));
    /** Half the rim's thickness, in blocks: the game's own half sixteenth. */
    private static final float RIM = 0.5F / 16F;

    private final BakedModel original;
    private final List<List<BakedQuad>> lods;
    @Nullable
    private final Relief relief;
    private final ItemOverrides overrides;

    private LiveModel(BakedModel original, @Nullable Relief relief, List<List<BakedQuad>> lods, Function<BakedModel, BakedModel> live) {
        this.original = original;
        this.relief = relief;
        this.lods = lods.stream().map(List::copyOf).toList();
        this.overrides = original.getOverrides() == ItemOverrides.EMPTY ? ItemOverrides.EMPTY : new Overrides(original, this, live);
    }

    /** effects: returns the flat model this one was made from: what the inventory and a frame show (D-0010) */
    BakedModel original() {
        return original;
    }

    /** effects: returns the relief this model is raised by, or null if it is extruded */
    @Nullable
    public Relief relief() {
        return relief;
    }

    /**
     * effects: returns {@code live} drawn flat (its original's quads) whenever the item is drawn in a slot or a frame
     * (HeftClient.drawingFlat), solid otherwise: for a model another mod draws past the item renderer's own choice
     */
    static BakedModel byView(LiveModel live) {
        return new net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel>(live) {
            @Override
            public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
                return HeftClient.drawingFlat() ? live.original.getQuads(state, side, random) : live.getQuads(state, side, random);
            }

            @Override
            public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                            net.neoforged.neoforge.client.model.data.ModelData data, @Nullable RenderType type) {
                return getQuads(state, side, random);
            }
        };
    }

    /** effects: returns the widest sprite this model is drawn with, as "name width" */
    String source() {
        SpriteContents best = null;
        for (BakedQuad q : lods.get(0)) {
            if (best == null || q.getSprite().contents().width() > best.width()) {
                best = q.getSprite().contents();
            }
        }
        return best.name() + " " + best.width();
    }

    /** effects: returns {@code original} raised by {@code relief} (extruded if null), or null if it has no quads or is not flat */
    @Nullable
    static LiveModel of(BakedModel original, @Nullable Relief relief, Function<BakedModel, BakedModel> live) {
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> all = new ArrayList<>(original.getQuads(null, null, random));
        for (Direction d : Direction.values()) {
            all.addAll(original.getQuads(null, d, random));
        }
        if (all.isEmpty() || !flat(all)) {
            // A pack's own 3D model (3D Default's ingots, say) is drawn as the pack made it.
            return null;
        }
        if (relief == null) {
            List<BakedQuad> out = extrude(all);
            return out.isEmpty() ? null : new LiveModel(original, null, List.of(out, out, out), live);
        }
        List<List<BakedQuad>> lods = new ArrayList<>(DETAILS);
        for (int k = 0; k < DETAILS; k++) {
            lods.add(inflateLayers(layers(all), relief, GRID >> k));
        }
        return lods.get(0).isEmpty() ? null : new LiveModel(original, relief, lods, live);
    }

    /**
     * effects: returns the item drawn from {@code sprite} alone (one untinted layer) raised by {@code relief}, held and shown as
     * the game's item/generated is, or null if the sprite is empty. For an item whose pack draws a 3D model that does
     * not read as the thing (D-0008): its own flat sprite is made solid instead.
     *
     * <p>Built lying (its face up, its top to the north) and stood up again by every view's right turn, so it looks as
     * any other in every view, while Set It Down, which rests a model drawn in place of the item's own by that model's
     * box as built, lays it on its face rather than standing it on its edge.
     */
    @Nullable
    static LiveModel ofSprite(TextureAtlasSprite sprite, Relief relief, BakedModel original) {
        List<List<BakedQuad>> lods = new ArrayList<>(DETAILS);
        for (int k = 0; k < DETAILS; k++) {
            lods.add(laid(inflateLayers(List.of(new Layer(sprite, -1)), relief, GRID >> k)));
        }
        return lods.get(0).isEmpty() ? null : new LiveModel(new Generated(original), relief, lods, m -> m);
    }

    /** effects: returns {@code quads} turned a quarter about x through the block's centre, so what faced south faces up */
    private static List<BakedQuad> laid(List<BakedQuad> quads) {
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad q : quads) {
            int[] v = q.getVertices().clone();
            for (int k = 0; k < 4; k++) {
                int i = k * STRIDE;
                float y = Float.intBitsToFloat(v[i + 1]) - 0.5F, z = Float.intBitsToFloat(v[i + 2]) - 0.5F;
                v[i + 1] = Float.floatToRawIntBits(0.5F + z);
                v[i + 2] = Float.floatToRawIntBits(0.5F - y);
                int n = v[i + 7];
                byte nx = (byte) n, ny = (byte) (n >> 8), nz = (byte) (n >> 16);
                v[i + 7] = (n & 0xFF000000) | (nx & 0xFF) | ((nz & 0xFF) << 8) | (((byte) -ny & 0xFF) << 16);
            }
            Direction d = switch (q.getDirection()) {
                case SOUTH -> Direction.UP;
                case NORTH -> Direction.DOWN;
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                default -> q.getDirection();
            };
            out.add(new BakedQuad(v, q.getTintIndex(), d, q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
        }
        return out;
    }

    /** effects: returns whether every vertex of {@code all} lies in the game's flat item slab (z 7.5 to 8.5 sixteenths) */
    static boolean flat(List<BakedQuad> all) {
        for (BakedQuad q : all) {
            int[] v = q.getVertices();
            for (int k = 0; k < 4; k++) {
                float z = Float.intBitsToFloat(v[k * STRIDE + 2]);
                if (z < 7.49F / 16F || z > 8.51F / 16F) {
                    return false;
                }
            }
        }
        return true;
    }

    /** effects: returns {@code all} thickened about the middle to DEPTH sixteenths, sprites and UVs kept */
    private static List<BakedQuad> extrude(List<BakedQuad> all) {
        List<BakedQuad> out = new ArrayList<>(all.size());
        for (BakedQuad q : all) {
            int[] v = q.getVertices().clone();
            for (int k = 0; k < 4; k++) {
                int i = k * STRIDE;
                float z = Float.intBitsToFloat(v[i + 2]);
                v[i + 2] = Float.floatToRawIntBits(0.5F + (z - 0.5F) * DEPTH);
            }
            out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
        }
        return out;
    }

    /** A layer of a flat model: its sprite and tint, as its front face draws it. */
    private record Layer(TextureAtlasSprite sprite, int tint) {}

    /** effects: returns the layers of the flat model {@code all} (its south faces), bottom first */
    private static List<Layer> layers(List<BakedQuad> all) {
        Map<String, Layer> seen = new LinkedHashMap<>();
        for (BakedQuad q : all) {
            if (q.getDirection() == Direction.SOUTH) {
                seen.putIfAbsent(q.getSprite().contents().name() + "|" + q.getTintIndex(), new Layer(q.getSprite(), q.getTintIndex()));
            }
        }
        return List.copyOf(seen.values());
    }

    /** effects: returns the layers raised by relief over an n-cell grid; empty if none has an opaque pixel */
    private static List<BakedQuad> inflateLayers(List<Layer> layers, Relief relief, int n) {
        if (layers.isEmpty()) {
            return List.of();
        }
        boolean[][] solid = cover(layers, n);
        float[][][] h = relief.heights(solid);
        float[][] front = Relief.corners(solid, h[0]), back = Relief.corners(solid, h[1]);
        List<BakedQuad> out = new ArrayList<>();
        for (int li = 0; li < layers.size(); li++) {
            Layer layer = layers.get(li);
            boolean[][] own = cover(List.of(layer), n);
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    own[j][i] &= solid[j][i];
                }
            }
            // Each layer's surface strays up to TOLERANCE from the heights, and so may the one beneath: lift it clear of both.
            float lift = li * (0.0015F + 2F * TOLERANCE / 16F);
            // Each side drawn with as few quads as keep it within TOLERANCE of its heights, with no cracks (Patches).
            surface(out, layer, own, front, n, true, lift);
            surface(out, layer, own, back, n, false, lift);
        }
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                if (solid[j][i]) {
                    rims(out, layers, solid, i, j, n);
                }
            }
        }
        return out;
    }

    /** effects: adds to out the front (or back) of the cells {@code own} of layer, raised by the corner heights c (sixteenths) */
    private static void surface(List<BakedQuad> out, Layer layer, boolean[][] own, float[][] c, int n, boolean front, float lift) {
        for (int[][] ring : Patches.outlines(Patches.cover(own, c, TOLERANCE), n)) {
            if (!front) {
                // The back is seen from behind: the same outline the other way round.
                int[][] r = new int[ring.length][];
                for (int k = 0; k < ring.length; k++) {
                    r[k] = ring[(ring.length - k) % ring.length];
                }
                ring = r;
            }
            for (int[] q : Patches.fan(ring.length)) {
                int[] v = new int[4 * STRIDE];
                for (int k = 0; k < 4; k++) {
                    corner(v, k, layer, c, ring[q[k]][0], ring[q[k]][1], n, front, lift);
                }
                out.add(new BakedQuad(v, layer.tint(), front ? Direction.SOUTH : Direction.NORTH, layer.sprite(), true, false));
            }
        }
    }

    /** effects: puts into v, as vertex k, grid corner (ci, cj) of the front (or back), raised by the corner heights c */
    private static void corner(int[] v, int k, Layer layer, float[][] c, int ci, int cj, int n, boolean front, float lift) {
        float sign = front ? 1F : -1F;
        float x = (float) ci / n, y = 1F - (float) cj / n;
        float z = 0.5F + sign * (RIM + c[cj][ci] / 16F + lift);
        // The slope at the corner, from its neighbours, for the normal: rise over run, both in cells.
        float dx = (c[cj][Math.min(ci + 1, n)] - c[cj][Math.max(ci - 1, 0)]) * n / 32F;
        float dy = (c[Math.min(cj + 1, n)][ci] - c[Math.max(cj - 1, 0)][ci]) * n / 32F;
        put(v, k, x, y, z, layer.sprite().getU((float) ci / n), layer.sprite().getV((float) cj / n), -dx * sign, dy * sign, sign);
    }

    /** effects: returns which cells of an n-grid over the layers' sprites have any opaque pixel in any of them */
    private static boolean[][] cover(List<Layer> layers, int n) {
        boolean[][] c = new boolean[n][n];
        for (Layer layer : layers) {
            SpriteContents s = layer.sprite().contents();
            int w = s.width(), hh = s.height();
            for (int j = 0; j < n; j++) {
                for (int i = 0; i < n; i++) {
                    // Every pixel the cell covers (one at least: a small sprite's cells share pixels).
                    int x0 = i * w / n, x1 = Math.max(x0 + 1, (i + 1) * w / n), y0 = j * hh / n, y1 = Math.max(y0 + 1, (j + 1) * hh / n);
                    for (int y = y0; y < y1 && !c[j][i]; y++) {
                        for (int x = x0; x < x1 && !c[j][i]; x++) {
                            c[j][i] = !s.isTransparent(0, x, y);
                        }
                    }
                }
            }
        }
        return c;
    }

    /** effects: adds to out a rim wall on each side of cell (i, j) of layers' cover that faces an empty cell or the edge */
    private static void rims(List<BakedQuad> out, List<Layer> layers, boolean[][] solid, int i, int j, int n) {
        // Coloured by an opaque texel of the cell, the first layer's that has one: a cell over several pixels may be clear
        // at its middle, and a wall drawn with a clear texel is cut away.
        Layer layer = layers.get(0);
        float u = layer.sprite().getU((i + 0.5F) / n), w = layer.sprite().getV((j + 0.5F) / n);
        found:
        for (Layer l : layers) {
            SpriteContents s = l.sprite().contents();
            int x0 = i * s.width() / n, x1 = Math.max(x0 + 1, (i + 1) * s.width() / n), y0 = j * s.height() / n, y1 = Math.max(y0 + 1, (j + 1) * s.height() / n);
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if (!s.isTransparent(0, x, y)) {
                        layer = l;
                        u = l.sprite().getU((x + 0.5F) / s.width());
                        w = l.sprite().getV((y + 0.5F) / s.height());
                        break found;
                    }
                }
            }
        }
        float x0 = (float) i / n, x1 = (float) (i + 1) / n, y0 = 1F - (float) (j + 1) / n, y1 = 1F - (float) j / n;
        float zb = 0.5F - RIM, zf = 0.5F + RIM;
        if (j == 0 || !solid[j - 1][i]) { // up
            out.add(wall(layer, u, w, Direction.UP, new float[][] {{x0, y1, zb}, {x0, y1, zf}, {x1, y1, zf}, {x1, y1, zb}}));
        }
        if (j == n - 1 || !solid[j + 1][i]) { // down
            out.add(wall(layer, u, w, Direction.DOWN, new float[][] {{x0, y0, zf}, {x0, y0, zb}, {x1, y0, zb}, {x1, y0, zf}}));
        }
        if (i == 0 || !solid[j][i - 1]) { // west
            out.add(wall(layer, u, w, Direction.WEST, new float[][] {{x0, y1, zb}, {x0, y0, zb}, {x0, y0, zf}, {x0, y1, zf}}));
        }
        if (i == n - 1 || !solid[j][i + 1]) { // east
            out.add(wall(layer, u, w, Direction.EAST, new float[][] {{x1, y1, zf}, {x1, y0, zf}, {x1, y0, zb}, {x1, y1, zb}}));
        }
    }

    private static BakedQuad wall(Layer layer, float u, float w, Direction d, float[][] p) {
        int[] v = new int[4 * STRIDE];
        for (int k = 0; k < 4; k++) {
            put(v, k, p[k][0], p[k][1], p[k][2], u, w, d.getStepX(), d.getStepY(), d.getStepZ());
        }
        return new BakedQuad(v, layer.tint(), d, layer.sprite(), true, false);
    }

    private static void put(int[] v, int k, float x, float y, float z, float u, float w, float nx, float ny, float nz) {
        int i = k * STRIDE;
        v[i] = Float.floatToRawIntBits(x);
        v[i + 1] = Float.floatToRawIntBits(y);
        v[i + 2] = Float.floatToRawIntBits(z);
        v[i + 3] = -1;
        v[i + 4] = Float.floatToRawIntBits(u);
        v[i + 5] = Float.floatToRawIntBits(w);
        v[i + 6] = 0;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        v[i + 7] = ((int) (nx / len * 127F) & 0xFF) | (((int) (ny / len * 127F) & 0xFF) << 8) | (((int) (nz / len * 127F) & 0xFF) << 16);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        if (side != null) {
            return List.of();
        }
        List<BakedQuad> quads = lods.get(HeftClient.detail());
        if (Perf.ON) {
            Perf.drew(quads.size());
        }
        return quads;
    }

    /** effects: returns how many quads this model draws at level of detail k (0 the finest) */
    public int quadCount(int k) {
        return lods.get(k).size();
    }

    /** effects: returns how many quads this model draws at each level of detail, as "near/mid/far" */
    String quadCounts() {
        return lods.get(0).size() + "/" + lods.get(1).size() + "/" + lods.get(2).size();
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return original.getRenderTypes(stack, fabulous);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return original.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return original.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return original.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return original.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return original.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return overrides;
    }

    /** The original's overrides, each model they pick made solid in turn. */
    private static final class Overrides extends ItemOverrides {
        private final BakedModel original;
        private final LiveModel self;
        private final Function<BakedModel, BakedModel> live;

        Overrides(BakedModel original, LiveModel self, Function<BakedModel, BakedModel> live) {
            this.original = original;
            this.self = self;
            this.live = live;
        }

        @Nullable
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel picked = original.getOverrides().resolve(original, stack, level, entity, seed);
            if (picked == null || picked == original) {
                return picked == null ? null : self;
            }
            return live.apply(picked);
        }
    }

    /**
     * A pack's model of an item seen as the game's item/generated would show it, drawn with a model built lying
     * ({@link #laid}): its transforms are item/generated's (vanilla's models/item/generated.json), each with a right
     * turn that stands the model up again; the rest the pack model's own.
     */
    private record Generated(BakedModel pack) implements BakedModel {
        private static ItemTransform t(float rx, float ry, float rz, float x, float y, float z, float s) {
            return new ItemTransform(new org.joml.Vector3f(rx, ry, rz), new org.joml.Vector3f(x / 16F, y / 16F, z / 16F), new org.joml.Vector3f(s, s, s),
                    new org.joml.Vector3f(90, 0, 0));
        }

        private static final ItemTransform THIRD = t(0, 0, 0, 0, 3, 1, 0.55F);
        private static final ItemTransform FIRST = t(0, -90, 25, 1.13F, 3.2F, 1.13F, 0.68F);
        // The inventory's is item/generated's none, but a transform of its own: the game skips NO_TRANSFORM, right turn and all.
        static final ItemTransforms TRANSFORMS = new ItemTransforms(THIRD, THIRD, FIRST, FIRST,
                t(0, 180, 0, 0, 13, 7, 1), t(0, 0, 0, 0, 0, 0, 1), t(0, 0, 0, 0, 2, 0, 0.5F), t(0, 180, 0, 0, 0, 0, 1));

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return pack.getQuads(state, side, random);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return pack.getRenderTypes(stack, fabulous);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return pack.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    /** Makes each flat model solid once, by one relief (extruded if none), until the next bake. */
    static final class Cache implements Function<BakedModel, BakedModel> {
        private final Map<BakedModel, BakedModel> made = new ConcurrentHashMap<>();
        @Nullable
        private final Relief relief;

        Cache(@Nullable Relief relief) {
            this.relief = relief;
        }

        @Override
        public BakedModel apply(BakedModel model) {
            if (model instanceof LiveModel || model.isGui3d() || model.isCustomRenderer()) {
                return model;
            }
            BakedModel done = made.get(model);
            if (done == null) {
                LiveModel l = of(model, relief, this);
                done = l == null ? model : l;
                made.put(model, done);
            }
            return done;
        }
    }
}

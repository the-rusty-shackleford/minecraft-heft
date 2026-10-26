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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A plant drawn as it grows (D-0005): its sprite on two planes crossed at 45 degrees, as the placed plant is
 * drawn, each layer with its tint, every plane seen from both sides -- for a flat block item whose block is a
 * bush or a coral. Drawn from the sprite repainted on Heft's atlas (the long tail's sheet, D-0004); held, framed
 * and in the inventory as a block is.
 *
 * <p>AF: the plant whose layers are the original's sprites, drawn as quads. RI: four quads per layer.
 */
final class CrossedModel implements BakedModel {
    /** Half the width of a plane across its diagonal, in blocks: the game's cross model (0.8 to 15.2 sixteenths, turned 45 degrees). */
    private static final float HALF = 0.5F * 14.4F / 16.0F;

    private final BakedModel original;
    private final List<BakedQuad> quads;

    private CrossedModel(BakedModel original, List<BakedQuad> quads) {
        this.original = original;
        this.quads = List.copyOf(quads);
    }

    /**
     * requires: {@code heft} looks up a sprite on Heft's atlas by name
     * effects: returns {@code original} drawn crossed, or null if a sprite of it has no repainted sheet
     */
    @Nullable
    static CrossedModel of(BakedModel original, Function<ResourceLocation, TextureAtlasSprite> heft) {
        Map<ResourceLocation, Integer> layers = new LinkedHashMap<>();
        for (BakedQuad q : original.getQuads(null, null, RandomSource.create(42L))) {
            layers.putIfAbsent(q.getSprite().contents().name(), q.getTintIndex());
        }
        if (layers.isEmpty()) {
            return null;
        }
        List<BakedQuad> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> layer : layers.entrySet()) {
            TextureAtlasSprite sprite = heft.apply(ExtrudedModel.sheet(layer.getKey()));
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return null;
            }
            float d = HALF * (float) Math.sqrt(0.5);
            // The two diagonals, each from one side and the other.
            float[][] planes = {{0.5F - d, 0.5F - d, 0.5F + d, 0.5F + d}, {0.5F - d, 0.5F + d, 0.5F + d, 0.5F - d}};
            for (float[] p : planes) {
                out.add(quad(sprite, layer.getValue(), p[0], p[1], p[2], p[3]));
                out.add(quad(sprite, layer.getValue(), p[2], p[3], p[0], p[1]));
            }
        }
        return new CrossedModel(original, out);
    }

    /** effects: returns an upright quad from (x0, z0) to (x1, z1), the whole sprite on it, facing to the left of that line */
    private static BakedQuad quad(TextureAtlasSprite sprite, int tint, float x0, float z0, float x1, float z1) {
        QuadBakingVertexConsumer b = new QuadBakingVertexConsumer();
        b.setSprite(sprite);
        b.setTintIndex(tint);
        b.setShade(true);
        b.setHasAmbientOcclusion(false);
        Vector3f n = new Vector3f(z1 - z0, 0, -(x1 - x0)).normalize();
        b.setDirection(Direction.getNearest(n.x, n.y, n.z));
        float[][] v = {{x0, 1, z0, 0, 0}, {x0, 0, z0, 0, 1}, {x1, 0, z1, 1, 1}, {x1, 1, z1, 1, 0}};
        for (float[] c : v) {
            b.addVertex(c[0], c[1], c[2]);
            b.setColor(-1);
            b.setUv(sprite.getU(c[3]), sprite.getV(c[4]));
            b.setUv2(0, 0);
            b.setNormal(n.x, n.y, n.z);
        }
        return b.bakeQuad();
    }

    /** The game's block display (block/block.json), so a plant is held, dropped and shown as a block is. */
    private static final ItemTransforms BLOCK = new ItemTransforms(
            t(75, 45, 0, 0, 2.5F, 0, 0.375F), t(75, 45, 0, 0, 2.5F, 0, 0.375F),
            t(0, 45, 0, 0, 0, 0, 0.4F), t(0, 45, 0, 0, 0, 0, 0.4F),
            ItemTransform.NO_TRANSFORM, t(30, 225, 0, 0, 0, 0, 0.625F), t(0, 0, 0, 0, 3, 0, 0.25F), t(0, 0, 0, 0, 0, 0, 0.5F),
            com.google.common.collect.ImmutableMap.of());

    private static ItemTransform t(float rx, float ry, float rz, float tx, float ty, float tz, float s) {
        return new ItemTransform(new Vector3f(rx, ry, rz), new Vector3f(tx, ty, tz).mul(1 / 16F), new Vector3f(s, s, s));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? quads : List.of();
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(HeftAtlas.ITEM_TRANSLUCENT);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
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
        return BLOCK;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}

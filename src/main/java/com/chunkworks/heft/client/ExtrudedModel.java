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

import com.chunkworks.heft.Heft;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * An item of the long tail drawn extruded (Tier B, D-0004): the game's own flat model of it -- a front, a
 * back and an edge along every run of its sprite's pixels, every layer with its tint -- made twice as thick
 * (two sixteenths, the handoff's §3.3 default) and drawn from its sprite repainted on Heft's atlas. Built
 * from the baked model, so any sprite size, any number of layers and any silhouette come out as the game
 * would draw them. A sprite with no repainted sheet leaves the item flat. Its overrides (a bow's draw, a
 * crossbow's load) resolve as the original's, each result extruded in turn.
 *
 * <p>AF: original, drawn with quads (original's, thickened, on Heft's atlas). RI: quads has one quad for
 * each of original's, in order.
 */
final class ExtrudedModel implements BakedModel {
    /** How many times the game's own thickness (one sixteenth): two sixteenths in all. */
    static final float THICKER = 2.0F;
    private static final int STRIDE = 8; // DefaultVertexFormat.BLOCK, in ints: x y z, colour, u v, light, normal

    private final BakedModel original;
    private final List<BakedQuad> quads;
    private final ItemOverrides overrides;

    private ExtrudedModel(BakedModel original, List<BakedQuad> quads, Function<BakedModel, BakedModel> extrude) {
        this.original = original;
        this.quads = List.copyOf(quads);
        this.overrides = original.getOverrides() == ItemOverrides.EMPTY ? ItemOverrides.EMPTY : new Overrides(original, this, extrude);
    }

    /**
     * requires: {@code heft} looks up a sprite on Heft's atlas by name
     * effects: returns {@code original} extruded, or null if one of its sprites has no repainted sheet;
     * {@code extrude} extrudes what its overrides resolve to
     */
    @Nullable
    static ExtrudedModel of(BakedModel original, Function<ResourceLocation, TextureAtlasSprite> heft, Function<BakedModel, BakedModel> extrude) {
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> out = new ArrayList<>();
        List<BakedQuad> all = new ArrayList<>(original.getQuads(null, null, random));
        for (Direction d : Direction.values()) {
            all.addAll(original.getQuads(null, d, random));
        }
        if (all.isEmpty()) {
            return null;
        }
        for (BakedQuad q : all) {
            TextureAtlasSprite from = q.getSprite();
            ResourceLocation name = from.contents().name();
            TextureAtlasSprite to = heft.apply(sheet(name));
            if (to.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                return null;
            }
            int[] v = q.getVertices().clone();
            for (int k = 0; k < 4; k++) {
                int i = k * STRIDE;
                float z = Float.intBitsToFloat(v[i + 2]);
                v[i + 2] = Float.floatToRawIntBits(0.5F + (z - 0.5F) * THICKER);
                float u = from.getUOffset(Float.intBitsToFloat(v[i + 4]));
                float w = from.getVOffset(Float.intBitsToFloat(v[i + 5]));
                v[i + 4] = Float.floatToRawIntBits(to.getU(u));
                v[i + 5] = Float.floatToRawIntBits(to.getV(w));
            }
            out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), to, q.isShade(), q.hasAmbientOcclusion()));
        }
        return new ExtrudedModel(original, out, extrude);
    }

    /** effects: returns the name of {@code sprite}'s repainted sheet on Heft's atlas: heft:gen/&lt;namespace&gt;/&lt;path&gt; */
    static ResourceLocation sheet(ResourceLocation sprite) {
        return Heft.id("gen/" + sprite.getNamespace() + "/" + sprite.getPath());
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

    /**
     * The original's overrides, each model they pick extruded (once per bake) or left as it is if it cannot be.
     */
    private static final class Overrides extends ItemOverrides {
        private final BakedModel original;
        private final ExtrudedModel self;
        private final Function<BakedModel, BakedModel> extrude;

        Overrides(BakedModel original, ExtrudedModel self, Function<BakedModel, BakedModel> extrude) {
            this.original = original;
            this.self = self;
            this.extrude = extrude;
        }

        @Nullable
        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
            BakedModel picked = original.getOverrides().resolve(original, stack, level, entity, seed);
            if (picked == null || picked == original) {
                return picked == null ? null : self;
            }
            return extrude.apply(picked);
        }
    }

    /**
     * Extrudes models the bake did not reach directly (an override's), each once, until the next bake.
     */
    static final class Cache implements Function<BakedModel, BakedModel> {
        private final Map<BakedModel, BakedModel> made = new ConcurrentHashMap<>();
        private final Function<ResourceLocation, TextureAtlasSprite> heft;

        Cache(Function<ResourceLocation, TextureAtlasSprite> heft) {
            this.heft = heft;
        }

        @Override
        public BakedModel apply(BakedModel model) {
            if (model instanceof ExtrudedModel || model.isGui3d() || model.isCustomRenderer()) {
                return model;
            }
            BakedModel done = made.get(model);
            if (done == null) {
                ExtrudedModel e = of(model, heft, this);
                done = e == null ? model : e;
                made.put(model, done);
            }
            return done;
        }
    }
}

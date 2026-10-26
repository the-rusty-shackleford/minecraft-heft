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
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A solid item's baked model: its shape's faces baked as the game bakes a model file's, on Heft's
 * atlas, drawn with {@link HeftAtlas#ITEM}. One model for every view, so Set It Down takes it for the
 * item's own and lays it down by its own item-frame turn. Its particle icon stays the item's own on
 * the block atlas, where breaking particles are drawn from.
 *
 * <p>AF: the item drawn as {@code quads} on Heft's atlas, posed per view by {@code transforms}.
 * RI: every quad's sprite is on Heft's atlas; particle is on the block atlas.
 */
final class SolidModel implements BakedModel {
    private final List<BakedQuad> quads;
    private final ItemTransforms transforms;
    private final TextureAtlasSprite particle;
    private final Solid solid;
    private final List<String> missing;

    private SolidModel(List<BakedQuad> quads, ItemTransforms transforms, TextureAtlasSprite particle, Solid solid, List<String> missing) {
        this.quads = List.copyOf(quads);
        this.transforms = transforms;
        this.particle = particle;
        this.solid = solid;
        this.missing = List.copyOf(missing);
    }

    /**
     * requires: {@code sprites} looks up sprites on any stitched atlas (the model bake's own getter)
     * effects: returns {@code solid} baked, its particle icon {@code original}'s
     */
    static SolidModel bake(Solid solid, BakedModel original, Function<Material, TextureAtlasSprite> sprites) {
        BlockModel model = solid.model();
        List<BakedQuad> out = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (BlockElement element : model.getElements()) {
            for (var face : element.faces.entrySet()) {
                Direction direction = face.getKey();
                BlockElementFace f = face.getValue();
                Material material = new Material(HeftAtlas.LOCATION, model.getMaterial(f.texture()).texture());
                TextureAtlasSprite sprite = sprites.apply(material);
                if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) && !missing.contains(material.texture().toString())) {
                    missing.add(material.texture().toString());
                }
                out.add(BlockModel.bakeFace(element, f, sprite, direction, BlockModelRotation.X0_Y0));
            }
        }
        return new SolidModel(out, model.getTransforms(), original.getParticleIcon(), solid, missing);
    }

    /** effects: returns what this model draws */
    Solid solid() {
        return solid;
    }

    /** effects: returns the sprites its faces name that Heft's atlas does not hold */
    List<String> missing() {
        return missing;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? quads : List.of();
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(HeftAtlas.ITEM);
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
        return particle;
    }

    @Override
    public ItemTransforms getTransforms() {
        return transforms;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}

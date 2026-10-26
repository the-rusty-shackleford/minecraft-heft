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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Heft's own atlas (D-0002): every solid item's sheet is stitched here, never on the block atlas,
 * which a 128x pack has already filled (Survivalist Armor's D-0008 measured it). Registered with the
 * model manager, so it is stitched with the game's mipmap levels and its sprites are there when
 * models bake; the shader mod finds each sheet's _n and _s beside it, as on any atlas.
 */
public final class HeftAtlas {
    private HeftAtlas() {}

    /** The atlas texture. */
    public static final ResourceLocation LOCATION = Heft.id("textures/atlas/items.png");

    /** Its sprite list, assets/heft/atlases/items.json, written by devtools/art/build.py. */
    public static final ResourceLocation INFO = Heft.id("items");

    /**
     * How a solid item is drawn: the game's own cutout entity drawing (as every item is), on this
     * atlas, sampled through its mipmaps -- 128 texels to the block shrink to an inventory slot without
     * shimmering.
     */
    public static final RenderType ITEM = RenderType.create("heft_item", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            true, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(LOCATION, false, true))
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true));

    /**
     * How an extruded item (Tier B) is drawn: as the game draws a flat item, translucent (a bottle's glass,
     * ice, a slime ball), on this atlas through its mipmaps.
     */
    public static final RenderType ITEM_TRANSLUCENT = RenderType.create("heft_item_translucent", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, true, true, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ITEM_ENTITY_TRANSLUCENT_CULL_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(LOCATION, false, true))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(true));
}

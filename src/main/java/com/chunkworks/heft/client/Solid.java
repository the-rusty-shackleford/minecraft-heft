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

import com.chunkworks.heft.domain.Shape;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One solid item as its shape file describes it.
 *
 * <p>AF: {@code item} drawn as {@code model} (a model file's elements and display, its textures on
 * Heft's atlas), a {@code klass} shape in {@code material}, of Set It Down size {@code size} (null:
 * normal); {@code shape} is the model's elements and item-frame turn, for checking against the canon;
 * {@code overPacks}: drawn in place of a pack's own plain model of the item too (D-0008).
 * RI: shape's elements are model's.
 */
public record Solid(ResourceLocation item, String klass, String material, @Nullable String size, BlockModel model, Shape shape,
                    boolean overPacks) {}

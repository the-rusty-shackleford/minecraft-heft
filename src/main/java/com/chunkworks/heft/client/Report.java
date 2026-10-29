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

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

/**
 * The progress bar (the handoff's §7.2): after every model bake, every item in the game sorted by how it is drawn --
 * raised (by a relief, D-0010), extruded, crossed (a plant), flat (still a sprite), 3D (a block, or a pack's own
 * model), custom (drawn by code), none (a model with nothing in it). One line to the log, the lists to
 * logs/heft-report.txt; logs/heft-live.txt has each made item's class, relief, sprite and quads.
 */
public final class Report {
    private Report() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** How an item is drawn. */
    public enum Kind { RAISED, EXTRUDED, CROSSED, FLAT, THREE_D, CUSTOM, NONE }

    /** effects: returns how {@code model} draws its item */
    public static Kind kind(BakedModel model) {
        if (model instanceof LiveModel live) {
            return live.relief() == null ? Kind.EXTRUDED : Kind.RAISED;
        }
        if (model instanceof CrossedModel) {
            return Kind.CROSSED;
        }
        if (model.isCustomRenderer()) {
            return Kind.CUSTOM;
        }
        RandomSource random = RandomSource.create(42L);
        boolean any = !model.getQuads(null, null, random).isEmpty();
        for (Direction d : Direction.values()) {
            any |= !model.getQuads(null, d, random).isEmpty();
        }
        if (!any) {
            return Kind.NONE;
        }
        // The game bakes a sprite's model (item/generated, item/handheld) not 3D; anything else is.
        return model.isGui3d() ? Kind.THREE_D : Kind.FLAT;
    }

    /** effects: writes the report on {@code models} to the log and logs/heft-report.txt */
    static void write(Map<ModelResourceLocation, BakedModel> models) {
        Map<Kind, List<ResourceLocation>> byKind = new TreeMap<>();
        for (Kind k : Kind.values()) {
            byKind.put(k, new ArrayList<>());
        }
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            BakedModel model = models.get(ModelResourceLocation.inventory(id));
            if (model != null) {
                byKind.get(kind(model)).add(id);
            }
        }
        byKind.values().forEach(list -> list.sort(null));
        LOG.info("Heft: {} raised, {} extruded, {} crossed, {} flat, {} 3D, {} custom, {} empty",
                byKind.get(Kind.RAISED).size(), byKind.get(Kind.EXTRUDED).size(), byKind.get(Kind.CROSSED).size(), byKind.get(Kind.FLAT).size(),
                byKind.get(Kind.THREE_D).size(), byKind.get(Kind.CUSTOM).size(), byKind.get(Kind.NONE).size());
        StringBuilder out = new StringBuilder("# Heft report: how every item is drawn after the last model bake\n");
        for (Map.Entry<Kind, List<ResourceLocation>> e : byKind.entrySet()) {
            out.append("\n## ").append(e.getKey().name().toLowerCase(java.util.Locale.ROOT)).append(" (").append(e.getValue().size()).append(")\n");
            Map<String, Integer> perMod = new TreeMap<>();
            e.getValue().forEach(id -> perMod.merge(id.getNamespace(), 1, Integer::sum));
            out.append("by mod: ").append(perMod).append('\n');
            e.getValue().forEach(id -> out.append(id).append('\n'));
        }
        Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("heft-report.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out);
        } catch (IOException e) {
            LOG.warn("Heft could not write {}: {}", file, e.toString());
        }
    }
}

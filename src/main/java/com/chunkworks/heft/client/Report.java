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

import com.chunkworks.heft.domain.Canon;
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
 * The progress bar (the handoff's §7.2): after every model bake, every item in the game sorted by
 * how it is drawn -- solid (Heft's), flat (still a sprite), 3D (a block, or another pack's model),
 * custom (drawn by code), none (a model with nothing in it) -- and every solid shape's breaches of the
 * canon and sprites missing from the atlas. One line to the log, the lists to logs/heft-report.txt.
 */
public final class Report {
    private Report() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** How an item is drawn. */
    public enum Kind { SOLID, FLAT, THREE_D, CUSTOM, NONE }

    /** effects: returns how {@code model} draws its item */
    public static Kind kind(BakedModel model) {
        if (model instanceof SolidModel) {
            return Kind.SOLID;
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
    static void write(Map<ModelResourceLocation, BakedModel> models, ShapeLibrary.Read read) {
        Map<Kind, List<ResourceLocation>> byKind = new TreeMap<>();
        for (Kind k : Kind.values()) {
            byKind.put(k, new ArrayList<>());
        }
        List<String> breaches = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            BakedModel model = models.get(ModelResourceLocation.inventory(id));
            if (model == null) {
                continue;
            }
            Kind kind = kind(model);
            byKind.get(kind).add(id);
            if (model instanceof SolidModel solid) {
                Canon.breaches(solid.solid().shape()).forEach(b -> breaches.add(id + ": " + b));
                solid.missing().forEach(m -> breaches.add(id + ": no sprite " + m + " on Heft's atlas"));
            }
        }
        byKind.values().forEach(list -> list.sort(null));
        LOG.info("Heft: {} solid, {} flat, {} 3D, {} custom, {} empty; {} breaches, {} shape files unread",
                byKind.get(Kind.SOLID).size(), byKind.get(Kind.FLAT).size(), byKind.get(Kind.THREE_D).size(),
                byKind.get(Kind.CUSTOM).size(), byKind.get(Kind.NONE).size(), breaches.size(), read.unread().size());
        breaches.forEach(b -> LOG.warn("Heft: {}", b));
        StringBuilder out = new StringBuilder("# Heft report: how every item is drawn after the last model bake\n");
        out.append("\n## breaches (").append(breaches.size()).append(")\n");
        breaches.forEach(b -> out.append(b).append('\n'));
        out.append("\n## unread shape files (").append(read.unread().size()).append(")\n");
        read.unread().forEach(b -> out.append(b).append('\n'));
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

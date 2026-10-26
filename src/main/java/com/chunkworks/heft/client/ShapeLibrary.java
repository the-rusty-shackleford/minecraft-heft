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
import com.chunkworks.heft.domain.Element;
import com.chunkworks.heft.domain.Shape;
import com.chunkworks.heft.domain.Turn;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import org.slf4j.Logger;

/**
 * Every solid item's shape file, read from assets/heft/shapes/&lt;namespace&gt;/&lt;item&gt;.json: a
 * model file (elements, textures, display) with a "heft" object naming its class, material and size.
 * Written by devtools/art/build.py; only Heft's own namespace is read, so no other pack can put a
 * model on its atlas.
 */
public final class ShapeLibrary {
    private ShapeLibrary() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String FOLDER = "shapes";

    /**
     * What a reading found: the solid items by item, and a line for each file that could not be read.
     */
    public record Read(Map<ResourceLocation, Solid> solids, List<String> unread) {}

    /** effects: returns every shape file {@code resources} holds, read; a file that cannot be read is skipped and named */
    public static Read read(ResourceManager resources) {
        Map<ResourceLocation, Solid> solids = new TreeMap<>();
        List<String> unread = new ArrayList<>();
        Map<ResourceLocation, Resource> files = resources.listResources(FOLDER,
                rl -> rl.getNamespace().equals(Heft.MOD_ID) && rl.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            String path = file.getKey().getPath();
            String[] parts = path.substring(FOLDER.length() + 1, path.length() - ".json".length()).split("/", 2);
            if (parts.length != 2) {
                unread.add(path + ": not shapes/<namespace>/<item>.json");
                continue;
            }
            ResourceLocation item = ResourceLocation.tryBuild(parts[0], parts[1]);
            if (item == null) {
                unread.add(path + ": not an item id");
                continue;
            }
            try (BufferedReader reader = file.getValue().openAsReader()) {
                String text = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                JsonObject heft = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("heft");
                BlockModel model = BlockModel.fromString(text);
                String size = heft.has("size") ? heft.get("size").getAsString() : null;
                solids.put(item, new Solid(item, heft.get("class").getAsString(), heft.get("material").getAsString(), size, model,
                        shape(model)));
            } catch (IOException | RuntimeException bad) {
                unread.add(path + ": " + bad);
            }
        }
        unread.forEach(line -> LOG.warn("Heft could not read a shape: {}", line));
        return new Read(solids, unread);
    }

    /** effects: returns {@code model}'s elements and item-frame turn as a domain shape */
    static Shape shape(BlockModel model) {
        List<Element> elements = new ArrayList<>();
        for (BlockElement e : model.getElements()) {
            double[] from = {e.from.x(), e.from.y(), e.from.z()};
            double[] to = {e.to.x(), e.to.y(), e.to.z()};
            if (e.rotation == null) {
                elements.add(Element.of(from, to));
            } else {
                elements.add(new Element(from, to, new double[] {e.rotation.origin().x(), e.rotation.origin().y(), e.rotation.origin().z()},
                        e.rotation.axis().ordinal(), e.rotation.angle(), e.rotation.rescale()));
            }
        }
        ItemTransform fixed = model.getTransforms().getTransform(ItemDisplayContext.FIXED);
        return new Shape(elements, Turn.euler(fixed.rotation.x(), fixed.rotation.y(), fixed.rotation.z()));
    }
}

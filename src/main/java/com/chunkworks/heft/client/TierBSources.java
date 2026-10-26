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
import com.chunkworks.heft.HeftConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

/**
 * Which sprites the long tail repaints (Tier B, D-0004): every layer texture of every item model that, as
 * the resources stand (a resource pack's 3D model counts), still resolves to the game's flat sprite model
 * (item/generated, and item/handheld through it) -- an override's model (a bow's draw) as much as an item's
 * own -- leaving out items with a Heft shape and items the settings leave flat. The models are read from
 * the merged resources, since what a player sees is what the winning pack says; the sprites themselves are
 * taken from their own mod ({@link OwnSprites}).
 */
final class TierBSources {
    private TierBSources() {}

    private static final int DEPTH = 16;
    private static final Set<String> FLAT_ROOTS = Set.of("minecraft:item/generated", "minecraft:builtin/generated");

    /** effects: returns the layer textures to repaint, in no particular order, read from {@code resources} */
    static Set<ResourceLocation> textures(ResourceManager resources) {
        Set<ResourceLocation> shaped = new java.util.HashSet<>();
        resources.listResources("shapes", rl -> rl.getNamespace().equals(Heft.MOD_ID) && rl.getPath().endsWith(".json")).keySet()
                .forEach(rl -> {
                    String[] p = rl.getPath().substring("shapes/".length(), rl.getPath().length() - ".json".length()).split("/", 2);
                    if (p.length == 2) {
                        shaped.add(ResourceLocation.fromNamespaceAndPath(p[0], p[1]));
                    }
                });
        Set<ResourceLocation> out = new LinkedHashSet<>();
        Map<ResourceLocation, Resource> files = resources.listResources("models/item",
                rl -> rl.getPath().endsWith(".json") && !rl.getNamespace().equals(Heft.MOD_ID));
        for (ResourceLocation file : files.keySet()) {
            String sub = file.getPath().substring("models/".length(), file.getPath().length() - ".json".length());
            String name = sub.substring("item/".length());
            if (!name.contains("/")) {
                ResourceLocation item = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
                if (shaped.contains(item) || !HeftConfig.extruded(item)) {
                    continue;
                }
            }
            List<ResourceLocation> layers = flatLayers(resources, ResourceLocation.fromNamespaceAndPath(file.getNamespace(), sub));
            if (layers != null) {
                out.addAll(layers);
            }
        }
        return out;
    }

    /** effects: returns the layer textures of model {@code id} if it resolves to the flat sprite model, else null */
    @Nullable
    static List<ResourceLocation> flatLayers(ResourceManager resources, ResourceLocation id) {
        Map<String, String> textures = new HashMap<>();
        ResourceLocation at = id;
        for (int depth = 0; depth < DEPTH; depth++) {
            JsonObject json = read(resources, at).orElse(null);
            if (json == null || json.has("elements") || json.has("loader")) {
                return null;
            }
            if (json.has("textures")) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                    textures.putIfAbsent(e.getKey(), e.getValue().getAsString());
                }
            }
            if (!json.has("parent")) {
                return null;
            }
            ResourceLocation parent = ResourceLocation.tryParse(json.get("parent").getAsString());
            if (parent == null) {
                return null;
            }
            if (FLAT_ROOTS.contains(parent.toString())) {
                List<ResourceLocation> layers = new ArrayList<>();
                for (int k = 0; k < 5; k++) {
                    String t = resolve(textures, textures.get("layer" + k));
                    ResourceLocation tex = t == null ? null : ResourceLocation.tryParse(t);
                    if (tex != null) {
                        layers.add(tex);
                    }
                }
                return layers;
            }
            at = parent;
        }
        return null;
    }

    @Nullable
    private static String resolve(Map<String, String> textures, @Nullable String value) {
        for (int i = 0; value != null && value.startsWith("#") && i < DEPTH; i++) {
            value = textures.get(value.substring(1));
        }
        return value == null || value.startsWith("#") ? null : value;
    }

    private static Optional<JsonObject> read(ResourceManager resources, ResourceLocation model) {
        ResourceLocation file = model.withPath(p -> "models/" + p + ".json");
        Optional<Resource> r = resources.getResource(file);
        if (r.isEmpty()) {
            return Optional.empty();
        }
        try (BufferedReader reader = r.get().openAsReader()) {
            JsonElement e = JsonParser.parseReader(reader);
            return e.isJsonObject() ? Optional.of(e.getAsJsonObject()) : Optional.empty();
        } catch (IOException | RuntimeException bad) {
            return Optional.empty();
        }
    }
}

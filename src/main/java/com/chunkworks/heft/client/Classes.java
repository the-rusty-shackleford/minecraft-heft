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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What each item is (D-0003): its class, from assets/heft/classes.json, which decides the relief it is raised by
 * (D-0010) and Set It Down's tags for it (devtools/tags.py writes those from the same file); and the items Heft draws
 * over a pack's own plain model (D-0008). Every spawn egg, any mod's, is of class spawn_egg.
 *
 * <p>AF: item {@code i} is of class {@code classes.get(i)}, none if absent; Heft draws its own over a pack's plain model of
 * each of {@code overPacks}. RI: overPacks's items are classes' keys.
 */
public record Classes(Map<ResourceLocation, String> classes, Set<ResourceLocation> overPacks) {
    private static final Logger LOG = LogUtils.getLogger();
    static final ResourceLocation FILE = Heft.id("classes.json");
    public static final String SPAWN_EGG = "spawn_egg";

    public Classes {
        classes = Map.copyOf(classes);
        overPacks = Set.copyOf(overPacks);
    }

    static final Classes NONE = new Classes(Map.of(), Set.of());

    /** effects: returns item {@code id}'s class: its own, or spawn_egg for any spawn egg, or null */
    @Nullable
    public String of(ResourceLocation id) {
        String own = classes.get(id);
        if (own != null) {
            return own;
        }
        return BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.SpawnEggItem ? SPAWN_EGG : null;
    }

    /** effects: returns the classes every resource pack's assets/heft/classes.json gives, later packs over earlier; logs what it could not read */
    static Classes read(ResourceManager resources) {
        Map<ResourceLocation, String> classes = new HashMap<>();
        Set<ResourceLocation> over = new HashSet<>();
        for (Resource r : resources.getResourceStack(FILE)) {
            try (Reader in = r.openAsReader()) {
                JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("items").entrySet()) {
                    classes.put(ResourceLocation.parse(e.getKey()), e.getValue().getAsString());
                }
                if (json.has("over_packs")) {
                    json.getAsJsonArray("over_packs").forEach(e -> over.add(ResourceLocation.parse(e.getAsString())));
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Heft could not read {} from {}: {}", FILE, r.sourcePackId(), e.toString());
            }
        }
        over.retainAll(classes.keySet());
        return new Classes(classes, over);
    }
}

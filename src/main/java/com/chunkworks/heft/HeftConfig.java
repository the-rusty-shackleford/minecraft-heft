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
package com.chunkworks.heft;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The player's settings, read when models are baked (a resource reload, F3+T, applies a change):
 * Heft on or off, classes left flat, and items or whole mods left flat.
 */
public final class HeftConfig {
    private HeftConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Draw flat items as solid ones. Takes effect on the next resource reload (F3+T).")
            .define("enabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLAT_CLASSES = BUILDER
            .comment("Classes left flat, by name: ingot, nugget, gem, dust, fruit, loaf, spawn_egg, ... Takes effect on the next resource reload.")
            .defineListAllowEmpty("flatClasses", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DENYLIST = BUILDER
            .comment("Items left flat: item ids (minecraft:filled_map) or whole mods (somemod:*). Takes effect on the next resource reload.")
            .defineListAllowEmpty("denylist", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * effects: returns whether {@code item}, of class {@code klass} (null: none), is drawn solid, as the settings stand
     * (the defaults before they are read)
     */
    public static boolean drawn(net.minecraft.resources.ResourceLocation item, @org.jetbrains.annotations.Nullable String klass) {
        if (!SPEC.isLoaded()) {
            return true;
        }
        if (!ENABLED.get() || (klass != null && FLAT_CLASSES.get().contains(klass))) {
            return false;
        }
        List<? extends String> denied = DENYLIST.get();
        return !denied.contains(item.toString()) && !denied.contains(item.getNamespace() + ":*");
    }
}

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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import org.slf4j.Logger;

/**
 * A sprite as its own mod ships it (D-0001): read from the game's own assets for its namespace, or from
 * the jar of the mod that carries it -- never from the merged resources, where a resource pack (Pixlli)
 * may have replaced it. A texture no mod ships, or one animated by an .mcmeta beside it, has no own sprite.
 */
final class OwnSprites {
    private OwnSprites() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** The mod files carrying assets in each namespace, found once. */
    private static final Map<String, List<IModFileInfo>> CARRIERS = new ConcurrentHashMap<>();

    /** effects: returns the PNG bytes of {@code texture} (e.g. minecraft:item/apple) as its own mod ships it, if it does and it is not animated */
    static Optional<byte[]> png(ResourceLocation texture) {
        String file = "textures/" + texture.getPath() + ".png";
        if (texture.getNamespace().equals("minecraft")) {
            Optional<byte[]> vanilla = vanilla(ResourceLocation.withDefaultNamespace(file));
            if (vanilla.isPresent()) {
                return vanilla(ResourceLocation.withDefaultNamespace(file + ".mcmeta")).isPresent() ? Optional.empty() : vanilla;
            }
        }
        for (IModFileInfo info : carriers(texture.getNamespace())) {
            Path png = info.getFile().findResource("assets", texture.getNamespace(), "textures", texture.getPath() + ".png");
            if (Files.isRegularFile(png)) {
                if (Files.exists(info.getFile().findResource("assets", texture.getNamespace(), "textures", texture.getPath() + ".png.mcmeta"))) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(Files.readAllBytes(png));
                } catch (IOException e) {
                    LOG.warn("Heft could not read {} from {}: {}", png, info.getFile().getFileName(), e.toString());
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<byte[]> vanilla(ResourceLocation file) {
        IoSupplier<InputStream> s = Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, file);
        if (s == null) {
            return Optional.empty();
        }
        try (InputStream in = s.get()) {
            return Optional.of(in.readAllBytes());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static List<IModFileInfo> carriers(String namespace) {
        return CARRIERS.computeIfAbsent(namespace, ns -> {
            List<IModFileInfo> out = new ArrayList<>();
            for (IModFileInfo info : ModList.get().getModFiles()) {
                if (Files.isDirectory(info.getFile().findResource("assets", ns))) {
                    out.add(info);
                }
            }
            return out;
        });
    }
}

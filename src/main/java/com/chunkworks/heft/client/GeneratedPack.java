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
import com.chunkworks.heft.domain.Repaint;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The long tail's sheets, made on the client (Tier B, D-0004): a hidden, always-on built-in pack serving
 * heft:textures/gen/&lt;namespace&gt;/&lt;path&gt;.png, with _n and _s beside it, for every sprite
 * {@link TierBSources} names, each repainted at 64x by {@link Repaint} from its own mod's sprite. Served as
 * resources so Heft's atlas lists them by a directory source and the shader mod finds their maps as it
 * finds any. A new instance is opened for every resource reload, so what it made last time is forgotten.
 *
 * <p>AF: for each source texture t with a repaintable own sprite, the sheet Repaint.of(t's sprite) at
 * textures/gen/t.ns/t.path.png (and its maps). RI: sheets is null until first asked, then fixed.
 */
final class GeneratedPack implements PackResources {
    static final String ID = "heft_generated";
    static final int PACK_FORMAT = 34;
    private static final Logger LOG = LogUtils.getLogger();
    private static final String DIR = "textures/gen/";

    private final PackLocationInfo location;
    @Nullable
    private volatile Map<String, Repaint.Sheet> sheets;

    GeneratedPack(PackLocationInfo location) {
        this.location = location;
    }

    /** effects: returns the sheets by their path under textures/gen/ (no .png), made the first time asked */
    private Map<String, Repaint.Sheet> sheets() {
        Map<String, Repaint.Sheet> s = sheets;
        if (s == null) {
            synchronized (this) {
                s = sheets;
                if (s == null) {
                    s = make();
                    sheets = s;
                }
            }
        }
        return s;
    }

    private static Map<String, Repaint.Sheet> make() {
        long start = System.nanoTime();
        Set<ResourceLocation> textures = TierBSources.textures(Minecraft.getInstance().getResourceManager());
        Map<String, Repaint.Sheet> out = new ConcurrentHashMap<>();
        int none = 0, odd = 0;
        for (ResourceLocation t : textures) {
            Optional<byte[]> png = OwnSprites.png(t);
            if (png.isEmpty()) {
                none++;
                continue;
            }
            try (NativeImage img = NativeImage.read(png.get())) {
                int w = img.getWidth(), h = img.getHeight();
                if (Repaint.sizeFor(w, h) == 0) {
                    odd++;
                    continue;
                }
                int[] argb = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        argb[y * w + x] = toArgb(img.getPixelRGBA(x, y));
                    }
                }
                out.put(t.getNamespace() + "/" + t.getPath(), Repaint.of(argb, w, h, t.toString().hashCode()));
            } catch (IOException | RuntimeException bad) {
                LOG.warn("Heft could not repaint {}: {}", t, bad.toString());
            }
        }
        LOG.info("Heft: {} sprites repainted for the extruded tier in {} ms; {} have no own sprite or are animated, {} are not square or an even size",
                out.size(), (System.nanoTime() - start) / 1_000_000, none, odd);
        return out;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(Heft.MOD_ID) || !(DIR.startsWith(path + "/") || path.startsWith("textures/gen"))) {
            return;
        }
        for (Map.Entry<String, Repaint.Sheet> e : sheets().entrySet()) {
            ResourceLocation file = Heft.id(DIR + e.getKey() + ".png");
            if (file.getPath().startsWith(path)) {
                Repaint.Sheet sheet = e.getValue();
                output.accept(file, () -> png(sheet.size(), sheet.albedo()));
            }
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        String path = location.getPath();
        if (type != PackType.CLIENT_RESOURCES || !location.getNamespace().equals(Heft.MOD_ID) || !path.startsWith(DIR) || !path.endsWith(".png")) {
            return null;
        }
        String key = path.substring(DIR.length(), path.length() - ".png".length());
        Map<String, Repaint.Sheet> s = sheets();
        Repaint.Sheet sheet = s.get(key);
        if (sheet != null) {
            return () -> png(sheet.size(), sheet.albedo());
        }
        if (key.endsWith("_n") && s.containsKey(key.substring(0, key.length() - 2))) {
            Repaint.Sheet base = s.get(key.substring(0, key.length() - 2));
            return () -> png(base.size(), base.normal());
        }
        if (key.endsWith("_s") && s.containsKey(key.substring(0, key.length() - 2))) {
            Repaint.Sheet base = s.get(key.substring(0, key.length() - 2));
            return () -> png(base.size(), base.specular());
        }
        return null;
    }

    /** effects: returns a size x size ARGB image as PNG bytes */
    private static InputStream png(int size, int[] argb) throws IOException {
        try (NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, false)) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    img.setPixelRGBA(x, y, toAbgr(argb[y * size + x]));
                }
            }
            return new ByteArrayInputStream(img.asByteArray());
        }
    }

    private static int toArgb(int abgr) {
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
    }

    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(Heft.MOD_ID) : Set.of();
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        if (deserializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("Heft: the long tail, repainted"), PACK_FORMAT);
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }
}

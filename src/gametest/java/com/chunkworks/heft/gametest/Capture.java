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
package com.chunkworks.heft.gametest;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * What a real render emits, counted: the vertices each render type was given, and the height they
 * span. The booth draws an item, a frame or a set-down display into one of these to tell whose model
 * was drawn -- Heft's, on its own atlas, or another -- without reading a pixel. From Survivalist
 * Armor's booth. Used on the render thread; never submitted.
 */
final class Capture implements MultiBufferSource {
    private final Map<RenderType, int[]> vertices = new LinkedHashMap<>();
    private float minY = Float.POSITIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        int[] n = vertices.computeIfAbsent(type, t -> new int[1]);
        return new VertexConsumer() {
            @Override
            public VertexConsumer addVertex(float x, float y, float z) {
                n[0]++;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                return this;
            }

            @Override
            public VertexConsumer setColor(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public VertexConsumer setUv(float u, float v) {
                return this;
            }

            @Override
            public VertexConsumer setUv1(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setUv2(int u, int v) {
                return this;
            }

            @Override
            public VertexConsumer setNormal(float x, float y, float z) {
                return this;
            }
        };
    }

    /** effects: returns the vertices given to {@code type} */
    int of(RenderType type) {
        int[] n = vertices.get(type);
        return n == null ? 0 : n[0];
    }

    /** effects: returns the vertices given to every render type */
    int total() {
        return vertices.values().stream().mapToInt(n -> n[0]).sum();
    }

    /** effects: returns the vertices given to every render type whose description mentions {@code text} */
    int mentioning(String text) {
        int sum = 0;
        for (Map.Entry<RenderType, int[]> e : vertices.entrySet()) {
            if (e.getKey().toString().contains(text)) {
                sum += e.getValue()[0];
            }
        }
        return sum;
    }

    /** effects: returns the height the vertices span */
    float height() {
        return maxY - minY;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        vertices.forEach((t, n) -> {
            String s = t.toString();
            b.append(s, 0, Math.min(s.length(), 90)).append(" = ").append(n[0]).append("; ");
        });
        return b.toString().trim();
    }
}

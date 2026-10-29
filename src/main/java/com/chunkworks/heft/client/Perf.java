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
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * A measurement of what drawing costs, on {@code -Dheft.perf=<seconds>} only: once a world is drawn, a warm-up of
 * {@code heft.perfWarmup} seconds (default 20), then the given seconds measured, written to {@code logs/heft-perf.txt}
 * a line every five seconds and a summary; then the game quits ({@code -Dheft.perfStay} keeps it open). Each line:
 * frames a second, frame times (mean, p50, p95, p99, max, in ms), the quads Heft's models handed out a frame and the
 * draws of them a frame, and the collector's pauses. {@code -Dheft.perfAt=x,y,z,yaw,pitch} measures from there.
 */
@EventBusSubscriber(modid = Heft.MOD_ID, value = Dist.CLIENT)
public final class Perf {
    private Perf() {}

    /** Whether a measurement runs: read once, so a draw pays one branch on a constant when it does not. */
    static final boolean ON = Integer.getInteger("heft.perf", 0) > 0;
    private static final long WARMUP = Integer.getInteger("heft.perfWarmup", 20) * 1_000_000_000L;
    private static final long SPAN = Integer.getInteger("heft.perf", 0) * 1_000_000_000L;
    private static final long WINDOW = 5_000_000_000L;

    /** Quads Heft's models have handed out, and the draws that took them, since the last frame (render thread). */
    private static long quads, draws;

    /** effects: notes a draw of n quads from one of Heft's models */
    static void drew(int n) {
        quads += n;
        draws++;
    }

    /** Draws at each level of detail since the measurement began (render thread). */
    private static final long[] AT_DETAIL = new long[4];

    /** effects: notes an item drawn at level of detail k */
    static void atDetail(int k) {
        if (windowStart >= 0) {
            AT_DETAIL[Math.min(k, AT_DETAIL.length - 1)]++;
        }
    }

    private static long first = -1, last = -1, windowStart = -1;
    private static final StringBuilder OUT = new StringBuilder();
    // Per window and over the whole measurement: frame times (ns), Heft's quads and draws.
    private static long[] frames = new long[4096], all = new long[1 << 16];
    private static int n, total;
    private static long wQuads, wDraws, tQuads, tDraws, gcCount0, gcTime0, wGcCount, wGcTime;

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.level == null || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen)) {
            quads = draws = 0;
            first = last = -1;
            return;
        }
        if (first < 0) {
            // -Dheft.perfAt=x,y,z,yaw,pitch: measured from there (singleplayer, commands allowed).
            String at = System.getProperty("heft.perfAt", "").replace(',', ' ');
            if (!at.isEmpty() && mc.player != null) {
                mc.player.connection.sendCommand("tp @s " + at);
            }
            first = last = now;
            quads = draws = 0;
            return;
        }
        long dt = now - last;
        last = now;
        long q = quads, d = draws;
        quads = draws = 0;
        if (now - first < WARMUP) {
            return;
        }
        if (windowStart < 0) {
            windowStart = now;
            long[] gc = gc();
            gcCount0 = wGcCount = gc[0];
            gcTime0 = wGcTime = gc[1];
            OUT.append(String.format("heft-perf at=%s tolerance=%s detail=%s grid=%d gui-scale=%s window=%dx%d%n", System.getProperty("heft.perfAt", "-"),
                    LiveModel.TOLERANCE, System.getProperty("heft.detail", "6,12"), LiveModel.GRID, mc.options.guiScale().get(), mc.getWindow().getWidth(), mc.getWindow().getHeight()));
            OUT.append("time\tfps\tmean\tp50\tp95\tp99\tmax\tquads/f\tdraws/f\tgc\tgc-ms\n");
        }
        if (n == frames.length) {
            frames = Arrays.copyOf(frames, n * 2);
        }
        frames[n++] = dt;
        if (total < all.length) {
            all[total++] = dt;
        }
        wQuads += q;
        wDraws += d;
        tQuads += q;
        tDraws += d;
        if (now - windowStart >= WINDOW) {
            long[] gc = gc();
            OUT.append(line(String.format("%.0f", (now - first - WARMUP) / 1e9), frames, n, now - windowStart, wQuads, wDraws,
                    gc[0] - wGcCount, gc[1] - wGcTime));
            wGcCount = gc[0];
            wGcTime = gc[1];
            n = 0;
            wQuads = wDraws = 0;
            windowStart = now;
        }
        if (now - first - WARMUP >= SPAN) {
            long[] gc = gc();
            long span = 0;
            for (int i = 0; i < total; i++) {
                span += all[i];
            }
            OUT.append(line("all", all, total, span, tQuads, tDraws, gc[0] - gcCount0, gc[1] - gcTime0));
            OUT.append(String.format("item draws by level of detail (near, mid, far): %d %d %d%n", AT_DETAIL[0], AT_DETAIL[1], AT_DETAIL[2]));
            try {
                java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("logs/heft-perf.txt"), OUT);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            if (Boolean.getBoolean("heft.perfStay")) {
                first = Long.MAX_VALUE / 2; // measured once
            } else {
                mc.stop();
            }
        }
    }

    private static String line(String label, long[] times, int count, long span, long q, long d, long gcs, long gcMs) {
        long[] s = Arrays.copyOf(times, count);
        Arrays.sort(s);
        double mean = span / 1e6 / Math.max(1, count);
        return String.format("%s\t%.1f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%.0f\t%d\t%d%n", label, count / (span / 1e9), mean,
                at(s, 0.50), at(s, 0.95), at(s, 0.99), s.length == 0 ? 0 : s[s.length - 1] / 1e6,
                q / Math.max(1, count), (double) d / Math.max(1, count), gcs, gcMs);
    }

    private static double at(long[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[Math.min(sorted.length - 1, (int) (p * sorted.length))] / 1e6;
    }

    /** effects: returns {pauses, ms paused} over every collector so far (ZGC's pause beans; cycles counted otherwise) */
    private static long[] gc() {
        long c = 0, t = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (b.getName().contains("Pauses") || !b.getName().startsWith("ZGC")) {
                c += Math.max(0, b.getCollectionCount());
                t += Math.max(0, b.getCollectionTime());
            }
        }
        return new long[] {c, t};
    }
}

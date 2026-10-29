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
import com.chunkworks.heft.domain.Relief;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The client's registrations: every flat item's model made solid from the sprite the game draws it with as models
 * are baked (D-0009, D-0010), and at draw time for a model another mod chose (D-0007); the view and distance each item
 * is drawn at; the report once models are baked; and {@code /heft inspect}.
 */
@EventBusSubscriber(modid = Heft.MOD_ID, value = Dist.CLIENT)
public final class HeftClient {
    private HeftClient() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** What each item is, as the last bake read it. */
    private static volatile Classes classes = Classes.NONE;

    /** effects: returns what each item is, as the last model bake read it */
    public static Classes classes() {
        return classes;
    }

    /**
     * Makes every flat item solid from the sprite the game draws it with, raised by what it is (D-0010) or extruded; a
     * plant crossed (D-0005); where a pack's 3D model does not read as the thing (D-0008), the item's own flat sprite
     * raised in its place. Drawn at the flat original's size, as the game draws every item: relative sizes are vanilla's.
     */
    @SubscribeEvent
    public static void onModify(ModelEvent.ModifyBakingResult event) {
        Classes read = Classes.read(Minecraft.getInstance().getResourceManager());
        classes = read;
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        Map<ResourceLocation, BakedModel> over = new java.util.HashMap<>();
        for (ResourceLocation id : read.overPacks()) {
            BakedModel original = models.get(ModelResourceLocation.inventory(id));
            if (original == null || !HeftConfig.drawn(id, read.of(id))) {
                continue;
            }
            TextureAtlasSprite sprite = event.getTextureGetter().apply(new Material(InventoryMenu.BLOCK_ATLAS,
                    ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "item/" + id.getPath())));
            Relief relief = LiveModel.relief(BuiltInRegistries.ITEM.get(id), id, read.of(id));
            LiveModel made = LiveModel.ofSprite(sprite, relief == null ? Relief.FOOD : relief, original);
            if (made != null) {
                over.put(id, made);
            }
        }
        overPacks = Map.copyOf(over);
        PLAIN.clear();
        SWAPPED.clear();
        Map<Optional<Relief>, LiveModel.Cache> caches = new java.util.HashMap<>();
        int made = 0;
        long t0 = System.nanoTime();
        java.util.Set<ResourceLocation> live = new java.util.TreeSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            ModelResourceLocation key = ModelResourceLocation.inventory(id);
            BakedModel model = models.get(key);
            if (model == null || model.isGui3d() || model.isCustomRenderer() || !HeftConfig.drawn(id, read.of(id))) {
                continue;
            }
            // A plant drawn crossed, as it grows (D-0005); never an item with a class: a carrot plants a crop, but is a root.
            if (read.of(id) == null && BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem block && growsCrossed(block.getBlock())) {
                CrossedModel crossed = CrossedModel.live(model);
                if (crossed != null) {
                    models.put(key, crossed);
                    made++;
                    continue;
                }
            }
            Relief relief = LiveModel.relief(BuiltInRegistries.ITEM.get(id), id, read.of(id));
            LiveModel.Cache cache = caches.computeIfAbsent(Optional.ofNullable(relief), r -> new LiveModel.Cache(r.orElse(null)));
            if (cache.apply(model) instanceof LiveModel done) {
                models.put(key, done);
                live.add(id);
                made++;
            }
        }
        // Spawn eggs another mod draws from models of its own, past the item's (VanillaBackport's vanillabackport:<egg>):
        // those models made solid too, flat in a slot or a frame by the view they are drawn in.
        int eggs = 0;
        for (ModelResourceLocation key : java.util.List.copyOf(models.keySet())) {
            ResourceLocation item = ResourceLocation.withDefaultNamespace(key.id().getPath());
            if (!key.variant().equals("inventory") || key.id().getNamespace().equals("minecraft") || !key.id().getPath().endsWith("_spawn_egg")
                    || !(BuiltInRegistries.ITEM.get(item) instanceof net.minecraft.world.item.SpawnEggItem)
                    || !HeftConfig.drawn(item, Classes.SPAWN_EGG)) {
                continue;
            }
            BakedModel model = models.get(key);
            Relief relief = LiveModel.relief(BuiltInRegistries.ITEM.get(item), item, Classes.SPAWN_EGG);
            if (model != null && !(model instanceof LiveModel) && new LiveModel.Cache(relief).apply(model) instanceof LiveModel done) {
                models.put(key, LiveModel.byView(done));
                eggs++;
            }
        }
        // logs/heft-live.txt: every item made solid, its class, its relief, the sprite it is drawn from, its quads near/mid/far.
        StringBuilder lines = new StringBuilder("item\tclass\trelief\tsprite\twidth\tquads\n");
        for (ResourceLocation id : live) {
            if (models.get(ModelResourceLocation.inventory(id)) instanceof LiveModel l) {
                String[] src = l.source().split(" ");
                lines.append(id).append('\t').append(java.util.Objects.requireNonNullElse(read.of(id), "-")).append('\t')
                        .append(java.util.Objects.toString(l.relief(), "extrude")).append('\t')
                        .append(src[0]).append('\t').append(src[1]).append('\t').append(l.quadCounts()).append('\n');
            }
        }
        try {
            java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("logs/heft-live.txt"), lines);
        } catch (java.io.IOException e) {
            LOG.warn("Heft could not write logs/heft-live.txt: {}", e.toString());
        }
        LOG.info("Heft: {} items made solid from their own sprites, {} other mods' spawn egg models, {} drawn over a pack's own (D-0008), in {} ms",
                made, eggs, over.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    /** effects: returns whether {@code block}, placed, is drawn crossed: a bush or a coral, but not what lies flat (a lily pad, petals) */
    private static boolean growsCrossed(net.minecraft.world.level.block.Block block) {
        return (block instanceof net.minecraft.world.level.block.BushBlock || block instanceof net.minecraft.world.level.block.BaseCoralPlantTypeBlock)
                && !(block instanceof net.minecraft.world.level.block.WaterlilyBlock) && !(block instanceof net.minecraft.world.level.block.PinkPetalsBlock);
    }

    /** Models met at draw time made solid, by the relief each is raised by (empty: extruded), until the next bake. */
    private static final Map<Optional<Relief>, LiveModel.Cache> AT_DRAW = new java.util.concurrent.ConcurrentHashMap<>();

    /** How many item frames are drawing their item now (render thread only): their FIXED view shows the flat sprite. */
    private static int framing = 0;

    /** effects: runs {@code draw}, an item frame drawing its item, noting that a frame is drawing */
    public static void framing(Runnable draw) {
        framing++;
        try {
            draw.run();
        } finally {
            framing--;
        }
    }

    /**
     * effects: returns whether {@code view} shows an item as the pack draws it, flat (D-0010): the inventory and every
     * other GUI slot, and an item frame. Every other view (held, dropped, set down, worn) is drawn solid.
     */
    public static boolean flatView(ItemDisplayContext view) {
        return view == ItemDisplayContext.GUI || (view == ItemDisplayContext.FIXED && framing > 0);
    }

    /** The view the item renderer is drawing in now (render thread only), null outside it. */
    @Nullable
    private static ItemDisplayContext drawing = null;

    /** Beyond these distances from the camera, in blocks, a raised item is drawn at its next level of detail. */
    private static final float[] DETAIL_AT = detailDistances(System.getProperty("heft.detail", "6,12"));
    /** The level of detail of the item the renderer is drawing now (render thread only): 0 the finest. */
    private static int detail = 0;

    private static float[] detailDistances(String s) {
        String[] parts = s.split(",");
        float[] d = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            float b = Float.parseFloat(parts[i].trim());
            d[i] = b * b;
        }
        return d;
    }

    /**
     * effects: runs {@code draw}, the item renderer drawing an item in {@code view} at {@code distanceSq} (blocks squared)
     * from the camera, noting the view and the level of detail it is drawn at
     */
    public static void inView(ItemDisplayContext view, float distanceSq, Runnable draw) {
        ItemDisplayContext was = drawing;
        int wasDetail = detail;
        drawing = view;
        int k = 0;
        while (k < DETAIL_AT.length && k + 1 < LiveModel.DETAILS && distanceSq > DETAIL_AT[k]) {
            k++;
        }
        detail = k;
        if (Perf.ON) {
            Perf.atDetail(k);
        }
        try {
            draw.run();
        } finally {
            drawing = was;
            detail = wasDetail;
        }
    }

    /** effects: returns the level of detail a raised item is drawn at now: 0 the finest, outside a draw too */
    static int detail() {
        return detail;
    }

    /** effects: returns whether the item being drawn now is shown flat (flatView of the view the renderer is drawing in) */
    static boolean drawingFlat() {
        return drawing != null && flatView(drawing);
    }

    /** effects: returns the flat model {@code model} was made solid from, or {@code model} if Heft did not make it */
    public static BakedModel flat(BakedModel model) {
        if (model instanceof LiveModel l) {
            return l.original();
        }
        if (model instanceof CrossedModel c) {
            return c.original();
        }
        return model;
    }

    /** What Heft drew in place of what it was handed, for an item in a view (D-0007). */
    public enum Decision {
        /** Heft's own model, made at the bake. */
        HEFT,
        /** A flat model another mod chose at draw time, made solid then. */
        AT_DRAW,
        /** A 3D model another mod or pack chose, left as it is. */
        LEFT_3D,
        /** Drawn by code of its own (a block entity renderer), left. */
        CUSTOM,
        /** A flat model left flat: the settings leave it, or it is not one Heft can make solid. */
        FLAT
    }

    private static final Map<String, Decision> DECISIONS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Item and view pairs already traced (-Dheft.traceDraw), each once per bake. */
    private static final java.util.Set<String> TRACED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** effects: returns what Heft last drew for {@code item} in {@code view}, or null if it has not been drawn there since the last bake */
    @Nullable
    public static Decision decided(ResourceLocation item, ItemDisplayContext view) {
        return DECISIONS.get(item + "|" + view.getSerializedName());
    }

    /** Heft's own model of each item drawn over a pack's (D-0008), from the last bake. */
    private static volatile Map<ResourceLocation, BakedModel> overPacks = Map.of();
    /** Whether a model another mod chose is that item's plain one, by identity: measured once a bake. */
    private static final Map<BakedModel, Boolean> PLAIN = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    /**
     * effects: returns the model whose transforms and quads are to be drawn for {@code stack} where another mod may have
     * chosen {@code model}: Heft's own where the item is drawn over a pack's and {@code model} is the pack's plain model
     * of it (D-0008); else {@code model}
     */
    public static BakedModel beforeDraw(ItemStack stack, BakedModel model) {
        if (stack.isEmpty() || overPacks.isEmpty() || model instanceof LiveModel) {
            return model;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        BakedModel own = overPacks.get(id);
        if (own == null) {
            return model;
        }
        if (!PLAIN.computeIfAbsent(model, m -> plain(id, m))) {
            return model;
        }
        SWAPPED.add(id);
        return own;
    }

    /** The items whose pack model Heft has drawn over since the last bake (D-0008), for the booth and the report. */
    private static final java.util.Set<ResourceLocation> SWAPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** effects: returns the items Heft draws over a pack's own plain model (D-0008), as the last bake placed them */
    public static java.util.Set<ResourceLocation> drawnOverPacks() {
        return overPacks.keySet();
    }

    /** effects: returns the items whose pack model has been swapped for Heft's since the last bake (D-0008) */
    public static java.util.Set<ResourceLocation> swapped() {
        return java.util.Set.copyOf(SWAPPED);
    }

    /**
     * effects: whether {@code model} is a pack's plain model of item {@code id}: every quad of it on the item's own
     * sprite ({@code item/<id>}) or its 3D one ({@code item/<id>_3d}), as Fresh Food names them. A plated, an
     * eating or a renamed variant draws other sprites and is left to the pack.
     */
    private static boolean plain(ResourceLocation id, BakedModel model) {
        String flat = id.getNamespace() + ":item/" + id.getPath();
        String deep = flat + "_3d";
        java.util.Set<String> sprites = new java.util.HashSet<>();
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(42L);
        model.getQuads(null, null, r).forEach(q -> sprites.add(q.getSprite().contents().name().toString()));
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            model.getQuads(null, d, r).forEach(q -> sprites.add(q.getSprite().contents().name().toString()));
        }
        return !sprites.isEmpty() && (sprites.equals(java.util.Set.of(flat)) || sprites.equals(java.util.Set.of(deep))
                || sprites.equals(java.util.Set.of(flat, deep)));
    }

    /**
     * effects: returns the model to draw for {@code stack} in {@code view} in place of {@code model}, which another mod
     * may have chosen at draw time: a flat one made solid as the bake would have made it (if the settings allow it),
     * anything else as it is; notes what it decided (D-0007)
     */
    public static BakedModel atDraw(ItemStack stack, ItemDisplayContext view, BakedModel model) {
        if (stack.isEmpty()) {
            return model;
        }
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        Decision d;
        BakedModel out = model;
        if (model instanceof LiveModel || model instanceof CrossedModel) {
            d = Decision.HEFT;
        } else if (model.isCustomRenderer()) {
            d = Decision.CUSTOM;
        } else if (model.isGui3d()) {
            d = Decision.LEFT_3D;
        } else if (!HeftConfig.drawn(id, classes.of(id))) {
            d = Decision.FLAT;
        } else {
            Relief relief = LiveModel.relief(item, id, classes.of(id));
            out = AT_DRAW.computeIfAbsent(Optional.ofNullable(relief), r -> new LiveModel.Cache(r.orElse(null))).apply(model);
            d = out instanceof LiveModel ? Decision.AT_DRAW : Decision.FLAT;
        }
        String key = id + "|" + view.getSerializedName();
        if (DECISIONS.get(key) != d) {
            DECISIONS.put(key, d);
        }
        if (Boolean.getBoolean("heft.traceDraw") && TRACED.add(key)) {
            LOG.info("Heft: {} in {} handed {} -> {}", id, view.getSerializedName(), model.getClass().getName(), d);
        }
        return out;
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        AT_DRAW.clear();
        DECISIONS.clear();
        TRACED.clear();
        Report.write(event.getModels());
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        InspectCommand.register(event.getDispatcher());
    }
}

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

import com.chunkworks.heft.client.HeftClient;
import com.chunkworks.heft.client.LiveModel;
import com.chunkworks.heft.client.Report;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Heft's items on film, in a flat world, among the mods that touch how items are drawn
 * (devtools/booth/fetch.py fills the game directory). Scenes: every item Heft has a class for in a frame on a wall
 * (flat, D-0010), and on the ground, and a stack of ingots; one of each class set down by Set It Down on a bench,
 * piles of four, and an ingot on a wall; three classes held in first person, and the third-person view; the
 * inventory full of them (flat). Ten others in frames, on the ground and in the hotbar: a lead, food, a bow, tinted
 * layers, thin things. In photos mode, the class lineup and the relief gallery.
 *
 * <p>The checks draw what they judge into a {@link Capture} and count the vertices: a raised item is drawn whole in
 * every view in the world, its finest quads up close and its coarsest far off, by nothing else; a slot and a frame
 * draw the flat model it was made from. One {@code booth: PASS} or {@code booth: FAIL} line per check; the Gradle
 * task reads them. Client only, active only under {@code heft.booth}: {@code checks} (the software booth) or
 * {@code photos} (the GPU's, shaders on).
 */
@EventBusSubscriber(modid = GameTestMod.MOD_ID, value = Dist.CLIENT)
public final class HeftBooth {
    private HeftBooth() {}

    private static final Logger LOG = LoggerFactory.getLogger("Heft booth");
    private static final String MODE = System.getProperty("heft.booth", "");
    private static final boolean PHOTOS = MODE.equals("photos");
    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    private enum Phase { TITLE, LOADING, PLACING, RUNNING, DONE }

    private record Step(int at, Runnable action) {}

    private static final int HOLD = 100;
    private static final int SETTLE = 60; // ticks after a camera move: chunk meshes rebuild after entities appear

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static int ground;
    private static volatile boolean placed = false;

    private static final BlockPos BENCH = new BlockPos(11, 0, 4);
    private static final BlockPos PILE = new BlockPos(13, 0, 4);
    /** The review row (D-0003, D-0006, D-0008): what changed, each beside what it is compared with, then meat by size. */
    private static final int REVIEW_X = 20, REVIEW_Z = 13;
    private static final List<String> REVIEW_ONCE = List.of("minecraft:bone", "minecraft:stick", "minecraft:coal", "minecraft:charcoal",
            "create:brass_ingot", "minecraft:gold_ingot", "create:brass_nugget", "subwild:coal_shard",
            "minecraft:bread", "minecraft:potato", "minecraft:baked_potato", "minecraft:porkchop");
    private static final List<String> REVIEW_MEAT = List.of("minecraft:cooked_porkchop", "farmersdelight:cooked_bacon",
            "farmersdelight:cod_slice", "farmersdelight:chicken_cuts", "minecraft:cooked_beef", "minecraft:cooked_chicken",
            "minecraft:cooked_mutton", "minecraft:cod");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (MODE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!muted) {
            // Silent from the first tick: someone may be listening to music while these run.
            mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            muted = true;
        }
        mc.getToasts().clear();
        switch (phase) {
            case TITLE -> {
                if (mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                    phase = Phase.LOADING;
                    createWorld(mc);
                }
            }
            case LOADING -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (mc.level != null && mc.player != null && mc.screen == null && server != null && mc.level.hasChunkAt(mc.player.blockPosition())) {
                    phase = Phase.PLACING;
                    mc.options.hideGui = true;
                    steps = plan(mc);
                    onServer(mc, HeftBooth::setUp);
                }
            }
            case PLACING -> {
                if (placed) {
                    phase = Phase.RUNNING;
                    tick = 0;
                }
            }
            case RUNNING -> {
                for (Step step : steps) {
                    if (step.at() == tick) {
                        step.action().run();
                    }
                }
                tick++;
            }
            case DONE -> { }
        }
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, null);
        LevelSettings settings = new LevelSettings("Heft booth", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("heft-booth", settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    // --- the scenes ----------------------------------------------------------------

    /** Every item in this game Heft has a class for, by class then name. */
    private static List<ItemStack> solids() {
        List<ItemStack> out = new ArrayList<>();
        HeftClient.classes().classes().entrySet().stream()
                .filter(e -> BuiltInRegistries.ITEM.containsKey(e.getKey()))
                .sorted(java.util.Map.Entry.<ResourceLocation, String>comparingByValue().thenComparing(e -> e.getKey().toString()))
                .forEach(e -> out.add(new ItemStack(BuiltInRegistries.ITEM.get(e.getKey()))));
        return out;
    }

    /** Samples of what has no class: a stick, food, a bow (overrides), tinted layers (a filled map, a dyed helmet, an egg), thin things. */
    private static List<ItemStack> tail() {
        List<ItemStack> out = new ArrayList<>(List.of(new ItemStack(Items.LEAD), new ItemStack(Items.INK_SAC), new ItemStack(Items.RABBIT_FOOT),
                new ItemStack(Items.BOW), new ItemStack(Items.FILLED_MAP), new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.FLINT_AND_STEEL),
                new ItemStack(Items.FEATHER), new ItemStack(Items.STRING), new ItemStack(Items.HONEYCOMB)));
        return out;
    }

    /** Phase 5's runtime kinds: plants drawn crossed, and spawn eggs drawn as one egg tinted per mob (D-0005). */
    private static final List<net.minecraft.world.item.Item> PLANTS = List.of(Items.DANDELION, Items.POPPY, Items.OAK_SAPLING,
            Items.CHERRY_SAPLING, Items.SHORT_GRASS, Items.FERN, Items.BLUE_ORCHID, Items.BRAIN_CORAL, Items.TORCHFLOWER);
    private static final List<net.minecraft.world.item.Item> EGGS = List.of(Items.CREEPER_SPAWN_EGG, Items.ZOMBIE_SPAWN_EGG,
            Items.PIG_SPAWN_EGG, Items.ENDERMAN_SPAWN_EGG, Items.SHEEP_SPAWN_EGG);

    /** One of each class, in class order. */
    private static List<ItemStack> oneOfEach() {
        java.util.Map<String, ItemStack> first = new java.util.TreeMap<>();
        for (ItemStack stack : solids()) {
            first.putIfAbsent(HeftClient.classes().of(BuiltInRegistries.ITEM.getKey(stack.getItem())), stack);
        }
        return new ArrayList<>(first.values());
    }

    /**
     * The relief gallery (D-0010): an item of each relief, and the two whose size was wrong (the spawn egg dropped, the
     * bread set down), each framed (flat), set down and dropped, close up; then held and in the inventory.
     */
    private static final List<String> GALLERY = List.of("minecraft:bread", "create:sweet_roll", "minecraft:apple", "minecraft:glow_berries",
            "minecraft:egg", "minecraft:creeper_spawn_egg", "minecraft:cookie", "minecraft:cooked_beef", "minecraft:cooked_porkchop",
            "minecraft:melon_slice", "minecraft:pumpkin_pie", "farmersdelight:roast_chicken", "minecraft:redstone",
            "farmersdelight:minced_beef", "minecraft:coal", "minecraft:raw_iron", "minecraft:gold_nugget", "minecraft:baked_potato",
            "minecraft:golden_carrot", "minecraft:cooked_chicken", "minecraft:cooked_mutton", "minecraft:bone", "minecraft:diamond",
            "minecraft:amethyst_shard", "minecraft:cod", "farmersdelight:fish_stew", "minecraft:shulker_shell", "minecraft:iron_ingot",
            "farmersdelight:egg_sandwich", "alexsmobs:cooked_catfish", "minecraft:poppy", "minecraft:paper");
    private static final int GALLERY_X = 20, GALLERY_Z = 60, GALLERY_ROW = 16, GALLERY_STEP = 3, GALLERY_ROWS_APART = 10;
    /** The gallery's items held in first person, by index. */
    private static final List<Integer> GALLERY_HELD = List.of(0, 2, 6, 7, 12, 22, 27);
    /** {@code heft.shots}: take only the shots whose name starts with it (all if unset). */
    private static final String SHOTS = System.getProperty("heft.shots", "");

    private static List<ItemStack> gallery() {
        List<ItemStack> out = new ArrayList<>();
        for (String id : GALLERY) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            out.add(item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
        }
        return out;
    }

    /** The class lineup (D-0009): one item of each class, framed, set down and dropped, rows of {@link #LINEUP_ROW}. */
    private static final int LINEUP_X = 20, LINEUP_Z = 20, LINEUP_ROW = 15, LINEUP_STEP = 3, LINEUP_ROWS_APART = 10;

    /**
     * effects: returns one item of each class, by class name: the first whose sprite (as the pack draws it) is under
     * 128 pixels wide, else the first; the same in every mode, since the particle sprite is the pack's in all of them
     */
    private static List<ItemStack> lineup() {
        java.util.Map<String, ItemStack> pick = new java.util.TreeMap<>();
        java.util.Map<String, int[]> wide = new java.util.TreeMap<>();
        var shaper = Minecraft.getInstance().getItemRenderer().getItemModelShaper();
        for (ItemStack stack : solids()) {
            String klass = HeftClient.classes().of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            int w = shaper.getItemModel(stack).getParticleIcon().contents().width();
            int[] n = wide.computeIfAbsent(klass, k -> new int[2]);
            n[w >= 128 ? 1 : 0]++;
            ItemStack had = pick.get(klass);
            if (had == null || (w < 128 && shaper.getItemModel(had).getParticleIcon().contents().width() >= 128)) {
                pick.put(klass, stack);
            }
        }
        wide.forEach((k, n) -> LOG.info("booth: lineup class {}: {} under 128x, {} at 128x or more; shown: {}", k, n[0], n[1],
                BuiltInRegistries.ITEM.getKey(pick.get(k).getItem())));
        return new ArrayList<>(pick.values());
    }

    private static void setUp(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        // The photographs' sun is low in the west, so relief and metal catch it.
        level.setDayTime(PHOTOS ? 10500L : 6000L);
        ground = level.getMinBuildHeight() + 4;
        sp.getInventory().clearContent();
        sp.setInvulnerable(true);
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        List<ItemStack> all = solids();
        // A wall to the north with a frame for every classed item, then the samples with no class, and an iron ingot in a
        // glow frame.
        fill(level, -21, 0, -9, 21, 11, -9, Blocks.STONE_BRICKS);
        List<ItemStack> framed = new ArrayList<>(all);
        framed.addAll(tail());
        for (int i = 0; i < framed.size(); i++) {
            int row = i / 40, col = i % 40;
            ItemFrame frame = new ItemFrame(level, new BlockPos(-20 + col, ground + 1 + row * 2, -8), Direction.SOUTH);
            frame.setItem(framed.get(i), false);
            level.addFreshEntity(frame);
        }
        GlowItemFrame glow = new GlowItemFrame(level, new BlockPos(20, ground + 11, -8), Direction.SOUTH);
        glow.setItem(new ItemStack(Items.IRON_INGOT), false);
        level.addFreshEntity(glow);
        // On the ground: every solid item, a row, and a stack of sixteen ingots (the game draws several copies).
        for (int i = 0; i < all.size(); i++) {
            ItemEntity drop = new ItemEntity(level, -19.5 + (i % 40), ground, 3.5 + (i / 40) * 1.2, all.get(i));
            drop.setNeverPickUp();
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
        }
        List<ItemStack> tail = tail();
        for (int i = 0; i < tail.size(); i++) {
            ItemEntity drop = new ItemEntity(level, -10.5 + i, ground, 11.5, tail.get(i));
            drop.setNeverPickUp();
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
        }
        for (int i = 0; i < PLANTS.size() + EGGS.size(); i++) {
            ItemStack kind = new ItemStack(i < PLANTS.size() ? PLANTS.get(i) : EGGS.get(i - PLANTS.size()));
            ItemEntity drop = new ItemEntity(level, -10.5 + i, ground, 13.5, kind);
            drop.setNeverPickUp();
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
        }
        ItemEntity stack = new ItemEntity(level, -12.5, ground, 11.5, new ItemStack(Items.IRON_INGOT, 16));
        stack.setNeverPickUp();
        stack.setUnlimitedLifetime();
        stack.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(stack);
        // The bench: one of each class set down, two blocks apart; beyond, piles of four ingots, diamonds and redstone.
        // Two rows of bench, every display within the distance the client tracks them from the camera's start.
        fill(level, 18, 0, 4, 56, 0, 4, Blocks.SMOOTH_STONE);
        fill(level, 18, 0, 7, 48, 0, 7, Blocks.SMOOTH_STONE);
        fill(level, 18, 0, 10, 48, 0, 10, Blocks.SMOOTH_STONE);
        List<ItemStack> each = oneOfEach();
        for (int i = 0; i < each.size(); i++) {
            setDown(sp, each.get(i).copy(), new BlockPos(BENCH.getX() + 8 + (i % 15) * 2, ground, BENCH.getZ() + (i / 15) * 3), Direction.UP);
        }
        Item[] piled = {Items.IRON_INGOT, Items.DIAMOND, Items.REDSTONE};
        for (int k = 0; k < piled.length; k++) {
            for (int i = 0; i < 4; i++) {
                setDown(sp, new ItemStack(piled[k]), new BlockPos(PILE.getX() + 36 + k * 2, ground, PILE.getZ()), Direction.UP);
            }
        }
        setDown(sp, new ItemStack(Items.IRON_INGOT), new BlockPos(-13, ground + 2, -9), Direction.SOUTH);
        // The review row: the materials once each, then four tries of each meat (a whole joint takes one, a cut piles).
        fill(level, REVIEW_X - 2, 0, REVIEW_Z, REVIEW_X + 2 * (REVIEW_ONCE.size() + REVIEW_MEAT.size()), 0, REVIEW_Z, Blocks.SMOOTH_STONE);
        for (int i = 0; i < REVIEW_ONCE.size() + REVIEW_MEAT.size(); i++) {
            boolean meat = i >= REVIEW_ONCE.size();
            String id = meat ? REVIEW_MEAT.get(i - REVIEW_ONCE.size()) : REVIEW_ONCE.get(i);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item == Items.AIR) {
                continue;
            }
            for (int n = 0; n < (meat ? 4 : 1); n++) {
                setDown(sp, new ItemStack(item), new BlockPos(REVIEW_X + i * 2, ground, REVIEW_Z), Direction.UP);
            }
        }
        // The class lineup: a two-high wall, a frame on it, a block with the item set down on it, the item dropped before it.
        List<ItemStack> lineup = PHOTOS ? lineup() : List.of();
        for (int i = 0; i < lineup.size(); i++) {
            int x = LINEUP_X + (i % LINEUP_ROW) * LINEUP_STEP, z = LINEUP_Z + (i / LINEUP_ROW) * LINEUP_ROWS_APART;
            fill(level, x - 1, 0, z, x + 1, 1, z, Blocks.STONE_BRICKS);
            fill(level, x, 0, z + 2, x, 0, z + 2, Blocks.SMOOTH_STONE);
            ItemFrame frame = new ItemFrame(level, new BlockPos(x, ground + 1, z + 1), Direction.SOUTH);
            frame.setItem(lineup.get(i).copy(), false);
            level.addFreshEntity(frame);
            setDown(sp, lineup.get(i).copy(), new BlockPos(x, ground, z + 2), Direction.UP);
            ItemEntity drop = new ItemEntity(level, x + 0.5, ground, z + 3.8, lineup.get(i).copy());
            drop.setNeverPickUp();
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
            LOG.info("booth: lineup {} at x {} z {}: {}", i, x, z, BuiltInRegistries.ITEM.getKey(lineup.get(i).getItem()));
        }
        // The relief gallery: the same, closer.
        List<ItemStack> gallery = PHOTOS ? gallery() : List.of();
        for (int i = 0; i < gallery.size(); i++) {
            if (gallery.get(i).isEmpty()) {
                LOG.info("booth: gallery {}: {} is not in this game", i, GALLERY.get(i));
                continue;
            }
            int x = GALLERY_X + (i % GALLERY_ROW) * GALLERY_STEP, z = GALLERY_Z + (i / GALLERY_ROW) * GALLERY_ROWS_APART;
            fill(level, x - 1, 0, z, x + 1, 1, z, Blocks.STONE_BRICKS);
            fill(level, x, 0, z + 2, x, 0, z + 2, Blocks.SMOOTH_STONE);
            ItemFrame frame = new ItemFrame(level, new BlockPos(x, ground + 1, z + 1), Direction.SOUTH);
            frame.setItem(gallery.get(i).copy(), false);
            level.addFreshEntity(frame);
            setDown(sp, gallery.get(i).copy(), new BlockPos(x, ground, z + 2), Direction.UP);
            ItemEntity drop = new ItemEntity(level, x + 0.5, ground, z + 3.3, gallery.get(i).copy());
            drop.setNeverPickUp();
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
        }
        // The inventory: the long tail's samples in the hotbar, then as many solid items as the rest holds.
        for (int i = 0; i < 9; i++) {
            sp.getInventory().setItem(i, tail.get(i).copy());
        }
        for (int i = 0; i < Math.min(27, all.size()); i++) {
            sp.getInventory().setItem(9 + i, all.get(i).copy());
        }
        aim(sp, new Vec3(0.5, ground + 2.2, 8.0), new Vec3(0.5, ground + 1.0, 0.5));
        placed = true;
    }

    /**
     * Meat by its size (D-0006): on Set It Down 1.2.0 or later, which reads the single tag first, four tries leave one
     * cooked beef and four cooked pork chops. On an older one the single tag is ignored, and the check says so.
     */
    private static void reviewMeat(Minecraft mc) {
        String version = net.neoforged.fml.ModList.get().getModContainerById("setitdown")
                .map(c -> c.getModInfo().getVersion().toString()).orElse("");
        if (version.isEmpty() || new org.apache.maven.artifact.versioning.DefaultArtifactVersion(version)
                .compareTo(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.2.0")) < 0) {
            LOG.info("booth: meat by size not checked: Set It Down {} does not read the single tag", version.isEmpty() ? "absent" : version);
            return;
        }
        int chop = REVIEW_ONCE.size(), beef = REVIEW_ONCE.size() + REVIEW_MEAT.indexOf("minecraft:cooked_beef");
        verdict("meat by size: four tries leave one cooked beef and four cooked pork chops (Set It Down " + version + ")", () -> {
            int beefs = reviewCount(mc, beef), chops = reviewCount(mc, chop);
            return beefs == 1 && chops == 4 ? null : beefs + " beef, " + chops + " chops";
        });
    }

    /** effects: returns how many items the Set It Down display at review place {@code i} holds, 0 if none */
    private static int reviewCount(Minecraft mc, int i) {
        BlockPos at = new BlockPos(REVIEW_X + i * 2, ground + 1, REVIEW_Z);
        for (Entity e : mc.level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(at), x -> true)) {
            if (e.getClass().getName().equals("com.chunkworks.setitdown.DisplayEntity")) {
                try {
                    return (int) e.getClass().getMethod("count").invoke(e);
                } catch (ReflectiveOperationException ex) {
                    return -1;
                }
            }
        }
        return 0;
    }

    /** effects: sets {@code stack} down on {@code face} of {@code at} through Set It Down's own placing, if Set It Down is loaded */
    private static void setDown(ServerPlayer sp, ItemStack stack, BlockPos at, Direction face) {
        try {
            Class<?> placing = Class.forName("com.chunkworks.setitdown.Placing");
            Vec3 hit = Vec3.atCenterOf(at).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            sp.teleportTo(hit.x + face.getStepX() * 1.5, hit.y + 0.2, hit.z + face.getStepZ() * 1.5 + (face == Direction.UP ? 1.5 : 0.0));
            sp.setItemInHand(InteractionHand.MAIN_HAND, stack);
            Object result = placing.getMethod("setDown", ServerPlayer.class, BlockPos.class, Direction.class, Vec3.class)
                    .invoke(null, sp, at, face, hit);
            LOG.info("booth: Set It Down placed {} on {} {}: {}", stack, at, face, result);
        } catch (ReflectiveOperationException e) {
            LOG.info("booth: Set It Down not here ({})", e.toString());
        }
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (BlockPos p : BlockPos.betweenClosed(x0, ground + y0, z0, x1, ground + y1, z1)) {
            level.setBlockAndUpdate(p, block.defaultBlockState());
        }
    }

    /** effects: puts the booth's player's eye at {@code from}, looking at {@code at} */
    private static void aim(ServerPlayer sp, Vec3 from, Vec3 at) {
        Vec3 d = at.subtract(from);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        sp.teleportTo(sp.serverLevel(), from.x, from.y - sp.getEyeHeight(), from.z, yaw, pitch);
    }

    // --- the film and the checks ------------------------------------------------------

    private static List<Step> plan(Minecraft mc) {
        List<Step> s = new ArrayList<>();
        int[] t = {HOLD};
        s.add(new Step(t[0], () -> checks(mc)));
        t[0] += 2;
        shot(s, t, mc, "booth-frames", new Vec3(0.0, 5.6, 9.0), new Vec3(0.0, 5.5, -8.5));
        shot(s, t, mc, "booth-frames-top-left", new Vec3(-12.5, 8.6, -5.6), new Vec3(-12.5, 8.5, -8.5));
        shot(s, t, mc, "booth-frames-top-mid", new Vec3(-4.5, 8.6, -5.6), new Vec3(-4.5, 8.5, -8.5));
        shot(s, t, mc, "booth-frames-top-right", new Vec3(3.5, 8.6, -5.6), new Vec3(3.5, 8.5, -8.5));
        shot(s, t, mc, "booth-frames-top-far", new Vec3(11.5, 8.6, -5.6), new Vec3(11.5, 8.5, -8.5));
        shot(s, t, mc, "booth-frames-left", new Vec3(-8.5, 2.6, -5.6), new Vec3(-8.5, 2.5, -8.5));
        shot(s, t, mc, "booth-frames-mid", new Vec3(-0.5, 2.6, -5.6), new Vec3(-0.5, 2.5, -8.5));
        shot(s, t, mc, "booth-frames-right", new Vec3(7.5, 2.6, -5.6), new Vec3(7.5, 2.5, -8.5));
        shot(s, t, mc, "booth-frames-upper-left", new Vec3(-8.5, 6.6, -5.6), new Vec3(-8.5, 6.5, -8.5));
        shot(s, t, mc, "booth-frames-upper-mid", new Vec3(-0.5, 6.6, -5.6), new Vec3(-0.5, 6.5, -8.5));
        shot(s, t, mc, "booth-frames-upper-right", new Vec3(7.5, 6.6, -5.6), new Vec3(7.5, 6.5, -8.5));
        shot(s, t, mc, "booth-ground", new Vec3(-0.5, 2.4, 9.5), new Vec3(-0.5, 0.1, 4.0));
        shot(s, t, mc, "booth-ground-left", new Vec3(-8.5, 1.1, 6.6), new Vec3(-8.5, 0.1, 3.8));
        shot(s, t, mc, "booth-ground-right", new Vec3(4.5, 1.1, 6.6), new Vec3(4.5, 0.1, 3.8));
        shot(s, t, mc, "booth-tail-ground", new Vec3(-6.0, 1.2, 14.4), new Vec3(-6.0, 0.1, 11.5));
        shot(s, t, mc, "booth-ground-back", new Vec3(-0.5, 1.4, 9.0), new Vec3(-0.5, 0.1, 6.0));
        shot(s, t, mc, "booth-kinds-ground", new Vec3(-4.0, 1.3, 16.4), new Vec3(-4.0, 0.2, 13.5));
        shot(s, t, mc, "booth-tail-frames", new Vec3(7.0, 9.6, -5.4), new Vec3(7.0, 9.5, -8.5));
        shot(s, t, mc, "booth-setdown", new Vec3(33.0, 3.0, 10.0), new Vec3(33.0, 1.05, 4.5));
        shot(s, t, mc, "booth-setdown-left", new Vec3(22.0, 1.6, 6.2), new Vec3(22.0, 1.05, 4.5));
        shot(s, t, mc, "booth-setdown-right", new Vec3(29.0, 1.6, 6.2), new Vec3(29.0, 1.05, 4.5));
        shot(s, t, mc, "booth-setdown-far", new Vec3(36.0, 1.6, 6.2), new Vec3(36.0, 1.05, 4.5));
        shot(s, t, mc, "booth-setdown-farther", new Vec3(43.0, 1.6, 6.2), new Vec3(43.0, 1.05, 4.5));
        shot(s, t, mc, "booth-setdown-back-left", new Vec3(22.0, 1.6, 9.2), new Vec3(22.0, 1.05, 7.5));
        shot(s, t, mc, "booth-setdown-back-right", new Vec3(29.0, 1.6, 9.2), new Vec3(29.0, 1.05, 7.5));
        shot(s, t, mc, "booth-setdown-back-far", new Vec3(36.0, 1.6, 9.2), new Vec3(36.0, 1.05, 7.5));
        shot(s, t, mc, "booth-setdown-back-farther", new Vec3(43.0, 1.6, 9.2), new Vec3(43.0, 1.05, 7.5));
        shot(s, t, mc, "booth-piles", new Vec3(51.5, 1.7, 6.6), new Vec3(51.5, 1.05, 4.5));
        // One shot a pair (a frame this close holds two places): the bone and the stick, coal and charcoal, ..., then the meats.
        for (int g = 0; g < (REVIEW_ONCE.size() + REVIEW_MEAT.size()) / 2; g++) {
            double x = REVIEW_X + g * 4 + 1.5;
            shot(s, t, mc, "booth-review-" + (g + 1), new Vec3(x, 1.55, REVIEW_Z + 1.9), new Vec3(x, 1.05, REVIEW_Z + 0.5));
        }
        // The last pair (cooked mutton and a cod) from above: how a set-down fish lies.
        double last = REVIEW_X + (REVIEW_ONCE.size() + REVIEW_MEAT.size() - 2) * 2 + 1.5;
        shot(s, t, mc, "booth-review-top", new Vec3(last, 2.9, REVIEW_Z + 1.0), new Vec3(last, 1.0, REVIEW_Z + 0.5));
        shot(s, t, mc, "booth-wall", new Vec3(-12.5, 2.6, -7.0), new Vec3(-12.5, 2.5, -8.5));
        // The class lineup, a class a shot: its frame, set down and dropped.
        int classes = PHOTOS ? lineup().size() : 0;
        for (int i = 0; i < classes; i++) {
            double x = LINEUP_X + (i % LINEUP_ROW) * LINEUP_STEP + 0.5, z = LINEUP_Z + (i / LINEUP_ROW) * LINEUP_ROWS_APART;
            shot(s, t, mc, String.format("booth-lineup-%02d", i), new Vec3(x, 2.2, z + 5.3), new Vec3(x, 1.1, z + 2.4));
        }
        // The relief gallery, an item a shot: its frame, set down and dropped, close.
        List<ItemStack> gallery = PHOTOS ? gallery() : List.of();
        if (!gallery.isEmpty() && ("booth-gallery".startsWith(SHOTS) || SHOTS.startsWith("booth-gallery"))) {
            // To the gallery's first place, and long enough there that its chunks have meshed before the first shot.
            s.add(new Step(t[0], () -> {
                hold(mc);
                onServer(mc, sp -> aim(sp, new Vec3(GALLERY_X + 0.5, ground + 2.05, GALLERY_Z + 6.0), new Vec3(GALLERY_X + 0.5, ground + 0.75, GALLERY_Z + 2.8)));
            }));
            t[0] += 200;
        }
        for (int i = 0; i < gallery.size(); i++) {
            if (gallery.get(i).isEmpty()) {
                continue;
            }
            double x = GALLERY_X + (i % GALLERY_ROW) * GALLERY_STEP + 0.5, z = GALLERY_Z + (i / GALLERY_ROW) * GALLERY_ROWS_APART;
            String name = GALLERY.get(i).substring(GALLERY.get(i).indexOf(':') + 1);
            shot(s, t, mc, String.format("booth-gallery-%02d-%s", i, name), new Vec3(x, 2.05, z + 6.0), new Vec3(x, 0.75, z + 2.8));
            shot(s, t, mc, String.format("booth-gallery-%02d-%s-side", i, name), new Vec3(x + 1.3, 1.25, z + 2.5), new Vec3(x, 1.05, z + 2.5));
        }
        // The gallery's first row from either side of each distance where a raised item's detail drops (6 and 12 blocks).
        if (!gallery.isEmpty()) {
            double mid = GALLERY_X + Math.min(gallery.size(), GALLERY_ROW) * GALLERY_STEP / 2.0;
            for (double back : new double[] {5.5, 6.5, 11.5, 12.5}) {
                shot(s, t, mc, String.format("booth-gallery-distance-%04.1f", back), new Vec3(mid, 1.6, GALLERY_Z + 2.8 + back),
                        new Vec3(mid, 0.75, GALLERY_Z + 2.8));
            }
        }
        if ("booth-gallery".startsWith(SHOTS) || SHOTS.startsWith("booth-gallery")) {
            for (int k : GALLERY_HELD) {
                if (k >= gallery.size() || gallery.get(k).isEmpty()) {
                    continue;
                }
                ItemStack held = gallery.get(k);
                String name = GALLERY.get(k).substring(GALLERY.get(k).indexOf(':') + 1);
                s.add(new Step(t[0], () -> {
                    // The hand is drawn only with the GUI shown (F1 hides both).
                    mc.options.hideGui = false;
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    onServer(mc, sp -> {
                        sp.getInventory().setItem(sp.getInventory().selected, held.copy());
                        aim(sp, new Vec3(0.5, ground + 3.0, -20.0), new Vec3(0.5, ground + 3.0, -40.0));
                    });
                }));
                s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-gallery-held-" + name)));
                t[0] += 2;
                s.add(new Step(t[0], () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
                s.add(new Step(t[0] += 20, () -> shoot(mc, "booth-gallery-held-" + name + "-third")));
                s.add(new Step(t[0] += 2, () -> mc.options.setCameraType(CameraType.FIRST_PERSON)));
            }
            s.add(new Step(t[0] += 4, () -> onServer(mc, sp -> {
                for (int i = 0; i < 36; i++) {
                    sp.getInventory().setItem(i, i < gallery.size() ? gallery.get(i).copy() : ItemStack.EMPTY);
                }
            })));
            s.add(new Step(t[0] += 10, () -> {
                mc.options.hideGui = false;
                mc.setScreen(new InventoryScreen(mc.player));
            }));
            s.add(new Step(t[0] += 20, () -> shoot(mc, "booth-gallery-inventory")));
            s.add(new Step(t[0] += 2, () -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
            }));
        }
        // The hand: standing, holding one of three classes in turn; then the third-person front view.
        List<ItemStack> each = oneOfEach();
        for (int k = 0; k < Math.min(3, each.size()); k++) {
            int slot = k;
            s.add(new Step(t[0], () -> {
                mc.options.hideGui = false;
                if (mc.player != null) {
                    mc.player.getAbilities().flying = false;
                    mc.player.setNoGravity(false);
                }
                onServer(mc, sp -> {
                    sp.setNoGravity(false);
                    sp.getAbilities().flying = false;
                    sp.onUpdateAbilities();
                    // Into the slot the client holds: a selected slot changed on the server is never sent to it.
                    sp.getInventory().setItem(sp.getInventory().selected, each.get(slot * 3 % each.size()).copy());
                    aim(sp, new Vec3(0.5, ground + sp.getEyeHeight(), 10.5), new Vec3(0.5, ground + 1.0, 0.5));
                });
            }));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-first-person-" + slot)));
            t[0] += 2;
        }
        s.add(new Step(t[0] += 4, () -> {
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-third-person")));
        s.add(new Step(t[0] += 4, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
            mc.setScreen(new InventoryScreen(mc.player));
        }));
        s.add(new Step(t[0] += 20, () -> shoot(mc, "booth-inventory")));
        s.add(new Step(t[0] += 2, () -> {
            mc.setScreen(null);
            mc.options.hideGui = true;
        }));
        s.add(new Step(t[0] += 10, () -> {
            verdict("all checks ran", () -> null);
            phase = Phase.DONE;
            mc.stop();
        }));
        return s;
    }

    /**
     * What is drawn: every classed item raised by its class's relief (or extruded, a class with none) in every view in
     * the world, whole, its finest quads up close and its coarsest far off, and nothing else; flat in a slot and a frame;
     * the samples with no class, the bow, the plants and the eggs with depth; D-0008's items Heft's; Set It Down's
     * displays drawing the item's model, posed by its own item-frame turn.
     */
    private static void checks(Minecraft mc) {
        List<ItemStack> all = solids();
        verdict("there are items with a class in this game (" + all.size() + ")", () -> all.isEmpty() ? "none" : null);
        List<String> notDeep = new ArrayList<>();
        for (ItemStack stack : tail()) {
            notDeep.addAll(flatIn(mc, stack));
        }
        verdict("the samples with no class have depth in every view in the world, whoever chose their model (D-0007)",
                () -> notDeep.isEmpty() ? null : String.valueOf(notDeep));
        verdict("a bow has depth in every view in the world", () -> {
            List<String> flat = flatIn(mc, new ItemStack(Items.BOW));
            return flat.isEmpty() ? null : String.valueOf(flat);
        });
        verdict("plants are drawn crossed, as they grow (D-0005)", () -> {
            List<String> wrong = new ArrayList<>();
            for (var plant : PLANTS) {
                Report.Kind k = Report.kind(mc.getItemRenderer().getModel(new ItemStack(plant), mc.level, null, 0));
                if (k != Report.Kind.CROSSED) {
                    wrong.add(BuiltInRegistries.ITEM.getKey(plant) + " " + k);
                }
            }
            return wrong.isEmpty() ? null : String.valueOf(wrong);
        });
        verdict("spawn eggs have depth in every view in the world, whoever draws them (D-0005, D-0007)", () -> {
            List<String> wrong = new ArrayList<>();
            for (var egg : EGGS) {
                wrong.addAll(flatIn(mc, new ItemStack(egg)));
            }
            return wrong.isEmpty() ? null : String.valueOf(wrong);
        });
        List<String> wrongKind = new ArrayList<>(), badViews = new ArrayList<>(), notFlat = new ArrayList<>(), badDetail = new ArrayList<>(),
                leftToPacks = new ArrayList<>(), madeAtDraw = new ArrayList<>();
        int[] near = {0, 0, 0}; // raised items, their finest quads in all, the most any one has
        for (ItemStack stack : all) {
            // What is drawn: the model chosen at draw time, with Heft's own where it draws over a pack's (D-0008).
            BakedModel model = HeftClient.beforeDraw(stack, mc.getItemRenderer().getModel(stack, mc.level, null, 0));
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            HeftClient.Decision decided = drawnAs(mc, stack, ItemDisplayContext.GROUND);
            if (packDraws(mc, stack) || decided == HeftClient.Decision.LEFT_3D || decided == HeftClient.Decision.CUSTOM) {
                leftToPacks.add(id.toString());
                continue;
            }
            if (decided == HeftClient.Decision.AT_DRAW) {
                // Another mod chose a flat model at draw time (Modefite, from a pack's item definition), made solid then by
                // the same relief; flatIn above and below judges its depth.
                madeAtDraw.add(id.toString());
                continue;
            }
            if (!(model instanceof LiveModel live)) {
                wrongKind.add(id + " " + Report.kind(model));
                continue;
            }
            Report.Kind want = LiveModel.relief(stack.getItem(), id, HeftClient.classes().of(id)) == null ? Report.Kind.EXTRUDED : Report.Kind.RAISED;
            if (Report.kind(live) != want && !HeftClient.drawnOverPacks().contains(id)) {
                wrongKind.add(id + " " + Report.kind(live) + ", not " + want);
            }
            if (Report.kind(live) == Report.Kind.RAISED) {
                near[0]++;
                near[1] += live.quadCount(0);
                near[2] = Math.max(near[2], live.quadCount(0));
            }
            for (ItemDisplayContext view : WORLD_VIEWS) {
                // Whole, and nothing else but the game's own enchantment glint over it.
                Capture c = drawn(mc, stack, view, 0);
                if (c.total() != live.quadCount(0) * 4 + c.mentioning("glint")) {
                    badViews.add(id + " in " + view.getSerializedName() + ": " + c.total() + " vertices, want " + live.quadCount(0) * 4);
                }
            }
            Capture far = drawn(mc, stack, ItemDisplayContext.GROUND, 20);
            if (far.total() != live.quadCount(LiveModel.DETAILS - 1) * 4 + far.mentioning("glint")
                    || (Report.kind(live) == Report.Kind.RAISED && live.quadCount(LiveModel.DETAILS - 1) >= live.quadCount(0))) {
                badDetail.add(id + ": " + far.total() + " vertices 20 blocks off, quads " + live.quadCount(0) + " near, "
                        + live.quadCount(LiveModel.DETAILS - 1) + " far");
            }
            int flatQuads = HeftClient.flat(live).getQuads(null, null, RandomSource.create(42L)).size();
            Capture slot = drawn(mc, stack, ItemDisplayContext.GUI, 0);
            if (slot.total() != flatQuads * 4 + slot.mentioning("glint")) {
                notFlat.add(id + " in a slot: " + slot.total() + " vertices, the flat model's " + flatQuads * 4);
            }
            int framed = framedVertices(mc, stack);
            if (framed != flatQuads * 4) {
                notFlat.add(id + " in a frame: " + framed + " vertices, the flat model's " + flatQuads * 4);
            }
        }
        LOG.info("booth: {} classed items another mod or pack draws in 3D of its own (D-0007): {}", leftToPacks.size(), leftToPacks);
        LOG.info("booth: {} classed items made solid at draw time from another mod's flat choice (D-0007): {}", madeAtDraw.size(), madeAtDraw);
        List<String> classedFlat = new ArrayList<>();
        for (ItemStack stack : all) {
            classedFlat.addAll(flatIn(mc, stack));
        }
        verdict("and none is drawn flat in any view in the world, whoever chose its model (D-0007)", () -> classedFlat.isEmpty() ? null : String.valueOf(classedFlat));
        LOG.info("booth: {} raised items, {} quads each up close on average, {} at most", near[0], near[0] == 0 ? 0 : near[1] / near[0], near[2]);
        verdict("every one is raised by its class's relief, or extruded if its class has none, unless a pack draws it in 3D of its own (D-0010)",
                () -> wrongKind.isEmpty() ? null : String.valueOf(wrongKind));
        verdict("every one is drawn whole in every view in the world, and by nothing else", () -> badViews.isEmpty() ? null : String.join("; ", badViews));
        verdict("every one shows the flat model it was made from in a slot and in a frame (D-0010)", () -> notFlat.isEmpty() ? null : String.join("; ", notFlat));
        verdict("every one is drawn 20 blocks off at its coarsest detail, a raised one in fewer quads than up close", () -> badDetail.isEmpty() ? null : String.join("; ", badDetail));
        // The stutter (D-0011): one quad a cell drew a raised item in about 1,000 quads, and facing a class lineup the game
        // ran at 13 frames a second. Merged, a raised item takes about half; this holds it there.
        verdict("a raised item takes at most " + NEAR_BUDGET_MEAN + " quads up close on average and " + NEAR_BUDGET_MAX + " at most (the stutter)",
                () -> near[0] > 0 && near[1] <= NEAR_BUDGET_MEAN * near[0] && near[2] <= NEAR_BUDGET_MAX ? null
                        : (near[0] == 0 ? 0 : near[1] / near[0]) + " on average, " + near[2] + " at most");
        List<Entity> displays = mc.level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(BENCH.above(ground)).inflate(50.0, 3.0, 8.0),
                e -> e.getClass().getName().equals("com.chunkworks.setitdown.DisplayEntity"));
        verdict("Set It Down set one of each class down, and the piles (" + displays.size() + " displays)",
                () -> displays.size() >= oneOfEach().size() + 3 ? null : displays.size() + " displays");
        reviewMeat(mc);
        // D-0008: what Heft draws over a pack's own is Heft's in every view in the world, whatever the pack chose.
        List<String> notOurs = new ArrayList<>();
        for (ResourceLocation id : HeftClient.drawnOverPacks()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            for (ItemDisplayContext view : WORLD_VIEWS) {
                HeftClient.Decision d = drawnAs(mc, stack, view);
                if (d != HeftClient.Decision.HEFT) {
                    notOurs.add(id + " in " + view.getSerializedName() + ": " + d);
                }
            }
        }
        LOG.info("booth: drawn over a pack's own model: {}", HeftClient.swapped());
        verdict("what Heft draws over a pack's own is Heft's in every view in the world (" + HeftClient.drawnOverPacks().size() + " items, D-0008)",
                () -> HeftClient.drawnOverPacks().isEmpty() ? "none placed" : notOurs.isEmpty() ? null : String.join("; ", notOurs));
        // Every set-down display draws its item's model (each copy whole, the glint aside), where Heft made that model.
        List<String> badDisplays = new ArrayList<>();
        for (Entity d : displays) {
            ItemStack held;
            int count;
            try {
                held = (ItemStack) d.getClass().getMethod("item").invoke(d);
                count = (int) d.getClass().getMethod("count").invoke(d);
            } catch (ReflectiveOperationException e) {
                badDisplays.add(e.toString());
                break;
            }
            BakedModel model = HeftClient.beforeDraw(held, mc.getItemRenderer().getModel(held, mc.level, null, 0));
            if (!(model instanceof LiveModel live) || packDraws(mc, held)) {
                continue;
            }
            Capture c = draw(mc, d);
            int vertices = c.total() - c.mentioning("glint");
            if (vertices != count * live.quadCount(0) * 4) {
                badDisplays.add(d.blockPosition() + " " + BuiltInRegistries.ITEM.getKey(held.getItem()) + " x" + count + ": " + vertices
                        + " vertices, want " + count * live.quadCount(0) * 4);
            }
        }
        verdict("every set-down display draws its item's solid model, each copy whole", () -> badDisplays.isEmpty() ? null : String.join("; ", badDisplays));
        verdict("Set It Down poses each class as the item's own model, by its own item-frame turn (not the hand's Rest)", () -> {
            List<String> rested = new ArrayList<>();
            try {
                var item = Class.forName("com.chunkworks.setitdown.client.BoundsCache")
                        .getMethod("item", ItemStack.class, net.minecraft.world.level.Level.class, int.class);
                for (ItemStack stack : oneOfEach()) {
                    // Only what Heft made as the item's own model: not a pack's 3D (Set It Down rests that by its own rule),
                    // and not what Heft draws over a pack's (D-0008), built lying because Set It Down measures the pack's.
                    if (packDraws(mc, stack) || drawnAs(mc, stack, ItemDisplayContext.GROUND) != HeftClient.Decision.HEFT
                            || HeftClient.drawnOverPacks().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                        continue;
                    }
                    Object entry = item.invoke(null, stack, mc.level, 0);
                    Object rest = entry.getClass().getMethod("rest").invoke(entry);
                    LOG.info("booth: Set It Down measures {} at rest as {}", stack.getItem(), entry.getClass().getMethod("box").invoke(entry));
                    if (rest != null) {
                        rested.add(stack.getItem() + " by Rest." + rest);
                    }
                }
            } catch (ReflectiveOperationException e) {
                return e.toString();
            }
            return rested.isEmpty() ? null : String.valueOf(rested);
        });
    }

    /**
     * The quads a raised item may take up close, on average and at most: in the pack's full instance (D-0011), 454 raised
     * items took 464 on average and 906 at most (a spawn egg), with room; one quad a cell had taken 988 and 1,976.
     */
    private static final int NEAR_BUDGET_MEAN = 600, NEAR_BUDGET_MAX = 1200;

    /** effects: returns what drawing {@code stack} in {@code view}, {@code back} blocks from the camera, gives */
    private static Capture drawn(Minecraft mc, ItemStack stack, ItemDisplayContext view, float back) {
        Capture c = new Capture();
        PoseStack pose = new PoseStack();
        pose.translate(0, 0, -back);
        mc.getItemRenderer().renderStatic(stack, view, LIGHT, OverlayTexture.NO_OVERLAY, pose, c, mc.level, 0);
        return c;
    }

    /** effects: returns the vertices a frame holding {@code stack} gives beyond an empty one, the glint aside: what it draws of the item */
    private static int framedVertices(Minecraft mc, ItemStack stack) {
        ItemFrame frame = new ItemFrame(mc.level, BlockPos.ZERO, Direction.SOUTH);
        int empty = draw(mc, frame).total();
        frame.setItem(stack.copy(), false);
        Capture c = draw(mc, frame);
        return c.total() - c.mentioning("glint") - empty;
    }

    /** effects: returns whether another mod or pack draws {@code stack} with a 3D model of its own (Fresh Food's apple), which Heft leaves (D-0007) */
    private static boolean packDraws(Minecraft mc, ItemStack stack) {
        BakedModel drawn = HeftClient.beforeDraw(stack, mc.getItemRenderer().getModel(stack, mc.level, null, 0));
        Report.Kind k = Report.kind(drawn);
        return k == Report.Kind.THREE_D || k == Report.Kind.CUSTOM;
    }

    /** effects: draws {@code stack} in {@code view} into nothing and returns what Heft decided for it there (D-0007) */
    private static HeftClient.Decision drawnAs(Minecraft mc, ItemStack stack, ItemDisplayContext view) {
        mc.getItemRenderer().renderStatic(stack, view, LIGHT, OverlayTexture.NO_OVERLAY, new PoseStack(), new Capture(), mc.level, 0);
        HeftClient.Decision d = HeftClient.decided(BuiltInRegistries.ITEM.getKey(stack.getItem()), view);
        // Never handed to the item renderer's drawing: another mod draws it with code of its own (VanillaBackport's
        // spawn eggs, while its use_legacy_spawn_eggs is off), which Heft leaves (D-0007).
        return d == null ? HeftClient.Decision.CUSTOM : d;
    }

    /** The views an item is seen in in the world, solid in all of them (D-0010); FIXED out of a frame is Set It Down's. */
    private static final List<ItemDisplayContext> WORLD_VIEWS = List.of(ItemDisplayContext.GROUND, ItemDisplayContext.FIXED,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.HEAD);

    /** effects: returns, for every view in the world {@code stack} is drawn flat in, a line naming it; empty if it has depth in every one */
    private static List<String> flatIn(Minecraft mc, ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (ItemDisplayContext view : WORLD_VIEWS) {
            if (drawnAs(mc, stack, view) == HeftClient.Decision.FLAT) {
                out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " in " + view.getSerializedName());
            }
        }
        return out;
    }

    /** effects: returns what drawing {@code entity} at the origin gives, through its renderer alone */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Capture draw(Minecraft mc, Entity entity) {
        Capture c = new Capture();
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        renderer.render(entity, 0.0F, 1.0F, new PoseStack(), c, LIGHT);
        return c;
    }

    /** One photograph: the player's eye moved to {@code from} (relative to the ground), looking at {@code at}. */
    private static void shot(List<Step> s, int[] t, Minecraft mc, String name, Vec3 from, Vec3 at) {
        if (!name.startsWith(SHOTS)) {
            return;
        }
        s.add(new Step(t[0], () -> {
            hold(mc);
            onServer(mc, sp -> {
                sp.setNoGravity(true);
                aim(sp, from.add(0, ground, 0), at.add(0, ground, 0));
            });
        }));
        s.add(new Step(t[0] += SETTLE, () -> {
            Vec3 want = from.add(0, ground, 0);
            Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
            if (eye.distanceTo(want) > 0.05) {
                LOG.warn("booth: {} taken from {}, not from {}", name, eye, want);
            }
            shoot(mc, name);
        }));
        t[0] += 2;
    }

    /**
     * Holds the camera still where a shot puts it: the client's own player flying, without gravity and at rest. The
     * server's flying flag alone does not hold it -- the client runs its own player's physics -- and a player
     * falling from every camera position to the ground, then teleported up for the next, shook the view.
     */
    private static void hold(Minecraft mc) {
        if (mc.player != null) {
            mc.player.getAbilities().flying = true;
            mc.player.setNoGravity(true);
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LOG.info("booth: {}", message.getString()));
    }

    /** Runs {@code check}; null is a pass, anything else the failure's detail. */
    private static void verdict(String what, Supplier<String> check) {
        String detail;
        try {
            detail = check.get();
        } catch (RuntimeException e) {
            LOG.error("booth: check threw", e);
            detail = e.toString();
        }
        if (detail == null) {
            LOG.info("booth: PASS {}", what);
        } else {
            LOG.error("booth: FAIL {} -- {}", what, detail.toLowerCase(Locale.ROOT).isEmpty() ? "?" : detail);
        }
    }
}

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

import com.chunkworks.heft.client.HeftAtlas;
import com.chunkworks.heft.client.HeftClient;
import com.chunkworks.heft.client.Solid;
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
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
import net.minecraft.world.inventory.InventoryMenu;
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
 * Solid items on film, in a flat world, among the mods that touch how items are drawn
 * (devtools/booth/fetch.py fills the game directory). Scenes: every solid item in a frame on a wall, and on
 * the ground, and a stack of ingots; one of each class set down by Set It Down on a bench, piles of four,
 * and an ingot on a wall; three classes held in first person, and the third-person view; the inventory
 * full of them. Flint is the loud twin (devtools/booth/loud.py): the iron ingot's colour with ridged
 * LabPBR normals, which a shader shows only if it reads Heft's maps. Ten of the long tail (Tier B, D-0004)
 * in frames, on the ground and in the hotbar: a lead, food, a bow, tinted layers, thin things.
 *
 * <p>The checks draw what they judge into a {@link Capture} and count what each render type was given:
 * a solid item is drawn whole on Heft's atlas in every view, and by nothing else. One {@code booth: PASS}
 * or {@code booth: FAIL} line per check; the Gradle task reads them. Client only, active only under
 * {@code heft.booth}: {@code checks} (the software booth) or {@code photos} (the GPU's, shaders on).
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
    /** The review row (Rusty's answers, 2026-10-26): what changed, each beside what it is compared with, then meat by size. */
    private static final int REVIEW_X = 20, REVIEW_Z = 13;
    private static final List<String> REVIEW_ONCE = List.of("minecraft:bone", "minecraft:stick", "minecraft:coal", "minecraft:charcoal",
            "create:brass_ingot", "minecraft:gold_ingot", "create:brass_nugget", "subwild:coal_shard");
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

    /** Every solid item in this game, by class then name: what the shape files name and the registry has. */
    private static List<ItemStack> solids() {
        List<ItemStack> out = new ArrayList<>();
        HeftClient.lastRead().solids().values().stream()
                .filter(s -> BuiltInRegistries.ITEM.containsKey(s.item()) && !s.material().equals("loud"))
                .sorted(java.util.Comparator.comparing((Solid s) -> s.klass()).thenComparing(s -> s.item().toString()))
                .forEach(s -> out.add(new ItemStack(BuiltInRegistries.ITEM.get(s.item()))));
        return out;
    }

    /** The long tail's samples (Tier B): a stick, food, a bow (overrides), tinted layers (a filled map, a dyed helmet, an egg), thin things. */
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
        for (Solid s : HeftClient.lastRead().solids().values()) {
            if (BuiltInRegistries.ITEM.containsKey(s.item()) && !s.material().equals("loud")) {
                first.putIfAbsent(s.klass(), new ItemStack(BuiltInRegistries.ITEM.get(s.item())));
            }
        }
        return new ArrayList<>(first.values());
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
        // A wall to the north with a frame for every solid item, two rows; then the loud twin (flint) and a
        // flat stick, for comparison, and an iron ingot in a glow frame.
        fill(level, -21, 0, -9, 21, 11, -9, Blocks.STONE_BRICKS);
        List<ItemStack> framed = new ArrayList<>(all);
        framed.add(new ItemStack(Items.FLINT));
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
        fill(level, REVIEW_X - 2, 0, REVIEW_Z, REVIEW_X + 32, 0, REVIEW_Z, Blocks.SMOOTH_STONE);
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
        shot(s, t, mc, "booth-wall", new Vec3(-12.5, 2.6, -7.0), new Vec3(-12.5, 2.5, -8.5));
        // Grazing light from the low sun across the frames: where the twin's ridges show, if the shader reads them.
        shot(s, t, mc, "booth-frames-grazing", new Vec3(13.0, 1.0, -7.0), new Vec3(9.0, 1.5, -8.5));
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
     * Whose model was drawn: every solid item's every view drawn into a {@link Capture}, its quads counted on
     * Heft's atlas and nothing anywhere else; the frames and the set-down displays the same way; the atlas
     * holding every sheet and the block atlas none.
     */
    private static void checks(Minecraft mc) {
        List<ItemStack> all = solids();
        verdict("there are solid items in this game (" + all.size() + ")", () -> all.isEmpty() ? "none" : null);
        // The long tail (Tier B, D-0004), judged by what is drawn: whoever chose the model (a pack through Modefite), a flat
        // one is drawn extruded on Heft's atlas (D-0007).
        List<String> notDeep = new ArrayList<>();
        for (ItemStack stack : tail()) {
            notDeep.addAll(flatIn(mc, stack));
        }
        verdict("the long tail's samples have depth in every view, whoever chose their model (Heft's, or a pack's 3D left)",
                () -> notDeep.isEmpty() ? null : String.valueOf(notDeep));
        verdict("a bow has depth in every view", () -> {
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
        verdict("spawn eggs have depth in every view: Heft's tinted egg, or another mod's own egg art extruded (D-0005, D-0007)", () -> {
            List<String> wrong = new ArrayList<>();
            for (var egg : EGGS) {
                wrong.addAll(flatIn(mc, new ItemStack(egg)));
                LOG.info("booth: {} in the gui: {}", BuiltInRegistries.ITEM.getKey(egg), drawnAs(mc, new ItemStack(egg), ItemDisplayContext.GUI));
            }
            return wrong.isEmpty() ? null : String.valueOf(wrong);
        });
        verdict("a repainted sheet is painted from the game's own sprite, never the winning pack's (D-0001)", () -> {
            var rm = mc.getResourceManager();
            int compared = 0;
            for (ItemStack stack : tail()) {
                ResourceLocation sprite = mc.getItemRenderer().getItemModelShaper().getItemModel(stack.getItem()).getParticleIcon().contents().name();
                ResourceLocation file = sprite.withPath(p -> "textures/" + p + ".png");
                var own = mc.getVanillaPackResources().getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES, file);
                var won = rm.getResource(file);
                var sheet = rm.getResource(com.chunkworks.heft.Heft.id("textures/gen/" + sprite.getNamespace() + "/" + sprite.getPath() + ".png"));
                if (own == null || won.isEmpty() || sheet.isEmpty()) {
                    continue;
                }
                try (var a = own.get(); var w = won.get().open(); var g = sheet.get().open();
                     var ownImg = com.mojang.blaze3d.platform.NativeImage.read(a); var wonImg = com.mojang.blaze3d.platform.NativeImage.read(w);
                     var sheetImg = com.mojang.blaze3d.platform.NativeImage.read(g)) {
                    if (ownImg.getWidth() == wonImg.getWidth() && java.util.Arrays.equals(ownImg.getPixelsRGBA(), wonImg.getPixelsRGBA())) {
                        continue;
                    }
                    // The pack replaced this sprite: the sheet must follow the game's own, texel for texel in its mask.
                    int n = ownImg.getWidth(), s = sheetImg.getWidth();
                    for (int y = 0; y < s; y++) {
                        for (int x = 0; x < s; x++) {
                            boolean ownOpaque = (ownImg.getPixelRGBA(x * n / s, y * n / s) >>> 24) != 0;
                            boolean sheetOpaque = (sheetImg.getPixelRGBA(x, y) >>> 24) != 0;
                            if (ownOpaque != sheetOpaque) {
                                return sprite + ": the sheet's mask is not the game's own sprite's at " + x + "," + y;
                            }
                        }
                    }
                    compared++;
                } catch (java.io.IOException e) {
                    return e.toString();
                }
            }
            LOG.info("booth: {} repainted sheets compared against a pack that replaced their sprite", compared);
            return null;
        });
        verdict("every sheet is on Heft's atlas, and none on the block atlas (D-0002)", () -> {
            TextureAtlas heft = mc.getModelManager().getAtlas(HeftAtlas.LOCATION);
            TextureAtlas blocks = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            List<String> wrong = new ArrayList<>();
            for (Solid s : HeftClient.lastRead().solids().values()) {
                ResourceLocation sheet = ResourceLocation.parse(s.model().getMaterial("0").texture().toString());
                if (heft.getSprite(sheet).contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                    wrong.add(sheet + " not on Heft's");
                }
                if (!blocks.getSprite(sheet).contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                    wrong.add(sheet + " on the block atlas");
                }
            }
            return wrong.isEmpty() ? null : String.join(", ", wrong);
        });
        List<String> notSolid = new ArrayList<>(), badParticle = new ArrayList<>(), badViews = new ArrayList<>(), leftToPacks = new ArrayList<>();
        for (ItemStack stack : all) {
            BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (packDraws(mc, stack) || drawnAs(mc, stack, ItemDisplayContext.GUI) != HeftClient.Decision.HEFT) {
                leftToPacks.add(id);
                continue;
            }
            if (Report.kind(model) != Report.Kind.SOLID) {
                notSolid.add(id);
                continue;
            }
            TextureAtlasSprite icon = model.getParticleIcon();
            if (!icon.atlasLocation().equals(InventoryMenu.BLOCK_ATLAS)) {
                badParticle.add(id);
            }
            int quads = model.getQuads(null, null, RandomSource.create(42L)).size();
            for (ItemDisplayContext view : ItemDisplayContext.values()) {
                if (view == ItemDisplayContext.NONE) {
                    continue;
                }
                Capture c = new Capture();
                mc.getItemRenderer().renderStatic(stack, view, LIGHT, OverlayTexture.NO_OVERLAY, new PoseStack(), c, mc.level, 0);
                // Whole on Heft's atlas, and nothing else but the game's own enchantment glint over it.
                if (quads == 0 || c.of(HeftAtlas.ITEM) != quads * 4 || c.total() != quads * 4 + c.mentioning("glint")) {
                    badViews.add(id + " in " + view.getSerializedName() + ": " + c);
                }
            }
        }
        LOG.info("booth: {} solid items another mod or pack chooses the model for (D-0007): {}", leftToPacks.size(), leftToPacks);
        List<String> flatViews = new ArrayList<>();
        for (ItemStack stack : all) {
            flatViews.addAll(flatIn(mc, stack));
        }
        verdict("every one is drawn solid, unless a pack draws it in 3D of its own", () -> notSolid.isEmpty() ? null : String.valueOf(notSolid));
        verdict("and none is drawn flat in any view, whoever chose its model (D-0007)", () -> flatViews.isEmpty() ? null : String.valueOf(flatViews));
        verdict("every shape keeps the canon, as the game reads it (D-0003)", () -> {
            List<String> breaches = new ArrayList<>();
            for (Solid s : HeftClient.lastRead().solids().values()) {
                com.chunkworks.heft.domain.Canon.breaches(s.shape()).forEach(x -> breaches.add(s.item() + ": " + x));
            }
            return breaches.isEmpty() ? null : String.join("; ", breaches);
        });
        verdict("every one's particle icon is its own, on the block atlas", () -> badParticle.isEmpty() ? null : String.valueOf(badParticle));
        verdict("every one is drawn whole on Heft's atlas in every view, and by nothing else", () -> badViews.isEmpty() ? null : String.join("; ", badViews));
        int[] frames = {0, 0};
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ItemFrame frame && Report.kind(mc.getItemRenderer().getModel(frame.getItem(), mc.level, null, 0)) == Report.Kind.SOLID) {
                frames[0]++;
                if (draw(mc, frame).of(HeftAtlas.ITEM) > 0) {
                    frames[1]++;
                }
            }
        }
        verdict("every frame holding a solid item draws it on Heft's atlas (" + frames[1] + " of " + frames[0] + ")",
                () -> frames[0] > 0 && frames[0] == frames[1] ? null : frames[1] + " of " + frames[0]);
        List<Entity> displays = mc.level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(BENCH.above(ground)).inflate(50.0, 3.0, 8.0),
                e -> e.getClass().getName().equals("com.chunkworks.setitdown.DisplayEntity"));
        verdict("Set It Down set one of each class down, and the piles (" + displays.size() + " displays)",
                () -> displays.size() >= oneOfEach().size() + 3 ? null : displays.size() + " displays");
        reviewMeat(mc);
        List<String> badDisplays = new ArrayList<>();
        // Where a pack-drawn item was set down: its display draws the pack's model, as it should.
        java.util.Set<BlockPos> packDrawnPositions = new java.util.HashSet<>();
        List<ItemStack> each = oneOfEach();
        for (int i = 0; i < each.size(); i++) {
            if (packDraws(mc, each.get(i)) || drawnAs(mc, each.get(i), ItemDisplayContext.GUI) != HeftClient.Decision.HEFT) {
                packDrawnPositions.add(new BlockPos(BENCH.getX() + 8 + (i % 15) * 2, ground + 1, BENCH.getZ() + (i / 15) * 3));
            }
        }
        for (Entity d : displays) {
            Capture c = draw(mc, d);
            if (c.of(HeftAtlas.ITEM) == 0 && c.of(HeftAtlas.ITEM_TRANSLUCENT) == 0 && c.mentioning("atlas/blocks") > 0 && !packDrawnPositions.contains(d.blockPosition())
                    && !packDrawnPositions.contains(d.blockPosition().below())) {
                badDisplays.add(d.blockPosition() + ": " + c);
            }
        }
        verdict("every set-down display draws on Heft's atlas", () -> badDisplays.isEmpty() ? null : String.join("; ", badDisplays));
        verdict("Set It Down poses each class as the item's own model, by its own item-frame turn (not the hand's Rest)", () -> {
            List<String> rested = new ArrayList<>();
            try {
                var item = Class.forName("com.chunkworks.setitdown.client.BoundsCache")
                        .getMethod("item", ItemStack.class, net.minecraft.world.level.Level.class, int.class);
                for (ItemStack stack : oneOfEach()) {
                    if (packDraws(mc, stack) || drawnAs(mc, stack, ItemDisplayContext.GUI) != HeftClient.Decision.HEFT) {
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

    /** effects: returns whether another mod or pack draws {@code stack} with a 3D model of its own (Fresh Food's apple), which Heft leaves (D-0007) */
    private static boolean packDraws(Minecraft mc, ItemStack stack) {
        BakedModel drawn = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
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

    /** The views an item is seen in. */
    private static final List<ItemDisplayContext> VIEWS = List.of(ItemDisplayContext.GUI, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

    /** effects: returns, for every view {@code stack} is drawn flat in, a line naming it; empty if it has depth in every one */
    private static List<String> flatIn(Minecraft mc, ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (ItemDisplayContext view : VIEWS) {
            if (drawnAs(mc, stack, view) == HeftClient.Decision.FLAT) {
                out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + " in " + view.getSerializedName());
            }
        }
        return out;
    }

    /** effects: returns the vertices drawing {@code stack} in {@code view} gives on Heft's atlas, either render type */
    private static int onHeft(Minecraft mc, ItemStack stack, ItemDisplayContext view) {
        Capture c = new Capture();
        mc.getItemRenderer().renderStatic(stack, view, LIGHT, OverlayTexture.NO_OVERLAY, new PoseStack(), c, mc.level, 0);
        return c.of(HeftAtlas.ITEM) + c.of(HeftAtlas.ITEM_TRANSLUCENT);
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

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
 * (devtools/booth/fetch.py fills the game directory). Scenes: item frames and a glow frame on a wall;
 * drops on the ground, one and a stack; ingots set down by Set It Down on a bench, one and a pile of
 * four, and one on a wall; the hand in first and third person; the inventory. The gold ingot is the
 * loud twin (devtools/booth/loud.py): the iron ingot's colour with ridged LabPBR normals, which a
 * shader shows only if it reads Heft's maps. The copper ingot is left flat, for comparison.
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
    private static final int SETTLE = 40;

    private static boolean muted = false;
    private static Phase phase = Phase.TITLE;
    private static int tick = 0;
    private static List<Step> steps;
    private static int ground;
    private static volatile boolean placed = false;

    private static final BlockPos BENCH = new BlockPos(11, 0, 4);
    private static final BlockPos PILE = new BlockPos(13, 0, 4);

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
        // A wall to the north with frames: iron (solid), the loud twin, copper (flat), and iron in a glow frame.
        fill(level, -4, 0, -9, 4, 3, -9, Blocks.STONE_BRICKS);
        ItemStack[] framed = {new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.COPPER_INGOT)};
        for (int i = 0; i < framed.length; i++) {
            ItemFrame frame = new ItemFrame(level, new BlockPos(-3 + i * 2, ground + 1, -8), Direction.SOUTH);
            frame.setItem(framed[i], false);
            level.addFreshEntity(frame);
        }
        GlowItemFrame glow = new GlowItemFrame(level, new BlockPos(3, ground + 1, -8), Direction.SOUTH);
        glow.setItem(new ItemStack(Items.IRON_INGOT), false);
        level.addFreshEntity(glow);
        // On the ground: one iron ingot, a stack of sixteen (the game draws several copies), the twin, copper.
        ItemStack[] dropped = {new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.GOLD_INGOT),
                new ItemStack(Items.COPPER_INGOT)};
        for (int i = 0; i < dropped.length; i++) {
            ItemEntity drop = new ItemEntity(level, -2.25 + i * 1.5, ground, 3.5, dropped[i]);
            drop.setNeverPickUp();
            drop.setUnlimitedLifetime();
            drop.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(drop);
        }
        // The bench: one iron ingot set down, and a pile of four beside it; one more on the wall's face.
        fill(level, 10, 0, 4, 14, 0, 4, Blocks.SMOOTH_STONE);
        setDown(sp, new ItemStack(Items.IRON_INGOT), BENCH.above(ground), Direction.UP);
        for (int i = 0; i < 4; i++) {
            setDown(sp, new ItemStack(Items.IRON_INGOT), PILE.above(ground), Direction.UP);
        }
        setDown(sp, new ItemStack(Items.IRON_INGOT), new BlockPos(-4, ground + 2, -9), Direction.SOUTH);
        // The hotbar: the solid ingot first (held), the twin, flat copper; a row of them in the inventory.
        sp.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT));
        sp.getInventory().setItem(1, new ItemStack(Items.GOLD_INGOT));
        sp.getInventory().setItem(2, new ItemStack(Items.COPPER_INGOT));
        sp.getInventory().setItem(3, new ItemStack(Items.IRON_INGOT, 64));
        for (int i = 0; i < 9; i++) {
            sp.getInventory().setItem(9 + i, new ItemStack(i % 3 == 0 ? Items.IRON_INGOT : i % 3 == 1 ? Items.GOLD_INGOT : Items.COPPER_INGOT));
        }
        aim(sp, new Vec3(0.5, ground + 2.2, 8.0), new Vec3(0.5, ground + 1.0, 0.5));
        placed = true;
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
        shot(s, t, mc, "booth-frames", new Vec3(0.0, 1.6, -6.4), new Vec3(0.0, 1.5, -8.5));
        shot(s, t, mc, "booth-frames-close", new Vec3(-1.0, 1.5, -7.3), new Vec3(-1.0, 1.5, -8.5));
        shot(s, t, mc, "booth-ground", new Vec3(0.0, 1.2, 5.6), new Vec3(0.0, 0.1, 3.5));
        shot(s, t, mc, "booth-ground-close", new Vec3(-1.5, 0.6, 4.7), new Vec3(-1.5, 0.1, 3.5));
        shot(s, t, mc, "booth-setdown", new Vec3(12.0, 1.9, 5.9), new Vec3(12.0, 1.05, 4.5));
        shot(s, t, mc, "booth-setdown-close", new Vec3(11.5, 1.45, 5.2), new Vec3(11.5, 1.05, 4.5));
        shot(s, t, mc, "booth-pile-close", new Vec3(13.5, 1.45, 5.2), new Vec3(13.5, 1.05, 4.5));
        shot(s, t, mc, "booth-wall", new Vec3(-3.5, 2.6, -7.0), new Vec3(-3.5, 2.5, -8.5));
        // Grazing light from the low sun across the frames: where the twin's ridges show, if the shader reads them.
        shot(s, t, mc, "booth-frames-grazing", new Vec3(1.8, 1.55, -7.2), new Vec3(-1.0, 1.5, -8.5));
        // The hand: standing, the solid ingot held; first person, then the third-person front view.
        s.add(new Step(t[0], () -> {
            mc.options.hideGui = false;
            onServer(mc, sp -> {
                sp.getAbilities().flying = false;
                sp.onUpdateAbilities();
                sp.getInventory().selected = 0;
                aim(sp, new Vec3(0.5, ground + sp.getEyeHeight(), 10.5), new Vec3(0.5, ground + 1.0, 0.5));
            });
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-first-person")));
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
     * Whose model was drawn: each view of the solid ingot drawn into a {@link Capture}, its quads counted
     * on Heft's atlas and nothing anywhere else; the frames and the set-down displays the same way.
     */
    private static void checks(Minecraft mc) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        BakedModel model = mc.getItemRenderer().getModel(iron, mc.level, null, 0);
        int quads = model.getQuads(null, null, RandomSource.create(42L)).size();
        verdict("the iron ingot is drawn solid", () -> Report.kind(model) == Report.Kind.SOLID ? null : Report.kind(model).toString());
        verdict("the copper ingot, which has no shape yet, is left as it was (flat)",
                () -> Report.kind(mc.getItemRenderer().getModel(new ItemStack(Items.COPPER_INGOT), mc.level, null, 0)) == Report.Kind.FLAT ? null : "not flat");
        verdict("its sheet is on Heft's atlas, and not on the block atlas (D-0002)", () -> {
            ResourceLocation sheet = ResourceLocation.parse("heft:solid/minecraft/iron_ingot");
            TextureAtlas heft = mc.getModelManager().getAtlas(HeftAtlas.LOCATION);
            TextureAtlas blocks = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            boolean onHeft = !heft.getSprite(sheet).contents().name().equals(MissingTextureAtlasSprite.getLocation());
            boolean onBlocks = !blocks.getSprite(sheet).contents().name().equals(MissingTextureAtlasSprite.getLocation());
            return onHeft && !onBlocks ? null : "on Heft's " + onHeft + ", on the block atlas " + onBlocks;
        });
        verdict("not one of Heft's sprites is on the block atlas", () -> {
            TextureAtlas heft = mc.getModelManager().getAtlas(HeftAtlas.LOCATION);
            TextureAtlas blocks = mc.getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            List<ResourceLocation> there = heft.getTextures().keySet().stream()
                    .filter(n -> !n.equals(MissingTextureAtlasSprite.getLocation())
                            && !blocks.getSprite(n).contents().name().equals(MissingTextureAtlasSprite.getLocation())).toList();
            return heft.getTextures().size() > 1 && there.isEmpty() ? null : heft.getTextures().size() + " on Heft's atlas; on the block atlas too: " + there;
        });
        verdict("its particle icon is the iron ingot's own, on the block atlas, where break particles are drawn from", () -> {
            TextureAtlasSprite icon = model.getParticleIcon();
            return icon.atlasLocation().equals(InventoryMenu.BLOCK_ATLAS) && icon.contents().name().getPath().endsWith("iron_ingot")
                    ? null : icon.contents().name() + " on " + icon.atlasLocation();
        });
        verdict("it has quads to draw", () -> quads > 0 ? null : "none");
        for (ItemDisplayContext view : ItemDisplayContext.values()) {
            if (view == ItemDisplayContext.NONE) {
                continue;
            }
            Capture c = new Capture();
            mc.getItemRenderer().renderStatic(iron, view, LIGHT, OverlayTexture.NO_OVERLAY, new PoseStack(), c, mc.level, 0);
            verdict("in " + view.getSerializedName() + " it is drawn whole on Heft's atlas, and nothing else",
                    () -> c.of(HeftAtlas.ITEM) == quads * 4 && c.total() == quads * 4 ? null : "Heft " + c.of(HeftAtlas.ITEM) + " of " + quads * 4 + ": " + c);
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ItemFrame frame && frame.getItem().is(Items.IRON_INGOT)) {
                Capture c = draw(mc, frame);
                verdict("an item frame draws it on Heft's atlas (" + frame.getType().toShortString() + ")",
                        () -> c.of(HeftAtlas.ITEM) == quads * 4 ? null : c.toString());
            }
        }
        List<Entity> displays = mc.level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(BENCH.above(ground)).inflate(3.0),
                e -> e.getClass().getName().equals("com.chunkworks.setitdown.DisplayEntity"));
        verdict("Set It Down set ingots down on the bench", () -> displays.isEmpty() ? "no display near " + BENCH.above(ground) : null);
        for (Entity d : displays) {
            Capture c = draw(mc, d);
            verdict("a set-down display at " + d.blockPosition() + " draws whole ingots on Heft's atlas",
                    () -> c.of(HeftAtlas.ITEM) > 0 && c.of(HeftAtlas.ITEM) % (quads * 4) == 0 ? null : c.toString());
        }
        verdict("Set It Down poses it as the item's own model, by its own item-frame turn (not the hand's Rest)", () -> {
            try {
                Object entry = Class.forName("com.chunkworks.setitdown.client.BoundsCache")
                        .getMethod("item", ItemStack.class, net.minecraft.world.level.Level.class, int.class).invoke(null, iron, mc.level, 0);
                Object rest = entry.getClass().getMethod("rest").invoke(entry);
                Object box = entry.getClass().getMethod("box").invoke(entry);
                LOG.info("booth: Set It Down measures the iron ingot at rest as {}", box);
                return rest == null ? null : "posed by Rest." + rest;
            } catch (ReflectiveOperationException e) {
                return e.toString();
            }
        });
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
        s.add(new Step(t[0], () -> onServer(mc, sp -> aim(sp, from.add(0, ground, 0), at.add(0, ground, 0)))));
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

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
import com.mojang.logging.LogUtils;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterMaterialAtlasesEvent;
import org.slf4j.Logger;

/**
 * The client's registrations: Heft's atlas with the model manager; every solid item's model put in
 * place of its own as models are baked; the report once they are; and {@code /heft inspect}.
 */
@EventBusSubscriber(modid = Heft.MOD_ID, value = Dist.CLIENT)
public final class HeftClient {
    private HeftClient() {}

    private static final Logger LOG = LogUtils.getLogger();

    /** What the last bake read, for the report and {@code /heft inspect}. */
    private static volatile ShapeLibrary.Read lastRead = new ShapeLibrary.Read(Map.of(), java.util.List.of());

    /** effects: returns what the last model bake read of the shape files */
    public static ShapeLibrary.Read lastRead() {
        return lastRead;
    }

    /**
     * The long tail's sheets: a hidden, always-on pack of Heft's own, made on the client (GeneratedPack).
     */
    @SubscribeEvent
    public static void onPacks(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Component title = Component.literal("Heft: the long tail, repainted");
        PackLocationInfo info = new PackLocationInfo(GeneratedPack.ID, title, PackSource.BUILT_IN, java.util.Optional.empty());
        Pack pack = new Pack(info, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new GeneratedPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new GeneratedPack(location);
            }
        }, new Pack.Metadata(title, PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), java.util.List.of(), true),
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    @SubscribeEvent
    public static void onAtlases(RegisterMaterialAtlasesEvent event) {
        event.register(HeftAtlas.LOCATION, HeftAtlas.INFO);
    }

    /**
     * Puts each solid item's model in place of its own. The shape files are read here, from the
     * resources this reload is baking from; Heft's atlas is stitched by now, so its sprites are baked in.
     */
    @SubscribeEvent
    public static void onModify(ModelEvent.ModifyBakingResult event) {
        ShapeLibrary.Read read = ShapeLibrary.read(Minecraft.getInstance().getResourceManager());
        lastRead = read;
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        int placed = 0;
        for (Solid solid : read.solids().values()) {
            if (!HeftConfig.solid(solid.klass())) {
                continue;
            }
            ModelResourceLocation key = ModelResourceLocation.inventory(solid.item());
            BakedModel original = models.get(key);
            if (original == null) {
                // No such item in this game: a mod the pack does not have.
                continue;
            }
            models.put(key, SolidModel.bake(solid, original, event.getTextureGetter()));
            placed++;
        }
        LOG.info("Heft: {} of {} shapes in place", placed, read.solids().size());
        // Every spawn egg, any mod's, is the egg of its kind, its base and spots tinted with the mob's colours (D-0005).
        Solid egg = read.kinds().get("spawn_egg");
        int eggs = 0;
        if (egg != null && HeftConfig.solid(egg.klass())) {
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                ModelResourceLocation key = ModelResourceLocation.inventory(id);
                BakedModel model = models.get(key);
                if (BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.SpawnEggItem && model != null && !(model instanceof SolidModel)
                        && !model.isGui3d()) {
                    models.put(key, SolidModel.bake(egg, model, event.getTextureGetter()));
                    eggs++;
                }
            }
        }
        LOG.info("Heft: {} spawn eggs", eggs);
        // The long tail (Tier B, D-0004): every item still drawn flat, extruded from its repainted sprite.
        java.util.function.Function<ResourceLocation, net.minecraft.client.renderer.texture.TextureAtlasSprite> heft =
                name -> event.getTextureGetter().apply(new net.minecraft.client.resources.model.Material(HeftAtlas.LOCATION, name));
        ExtrudedModel.Cache extrude = new ExtrudedModel.Cache(heft);
        int extruded = 0, left = 0;
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            ModelResourceLocation key = ModelResourceLocation.inventory(id);
            BakedModel model = models.get(key);
            if (model == null || model instanceof SolidModel || model.isGui3d() || model.isCustomRenderer() || !HeftConfig.extruded(id)) {
                continue;
            }
            // A plant is drawn crossed, as it grows (D-0005); everything else of the long tail extruded.
            if (BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem block && growsCrossed(block.getBlock())) {
                CrossedModel crossed = CrossedModel.of(model, heft);
                if (crossed != null) {
                    models.put(key, crossed);
                    extruded++;
                    continue;
                }
            }
            BakedModel done = extrude.apply(model);
            if (done instanceof ExtrudedModel) {
                models.put(key, done);
                extruded++;
            } else {
                left++;
            }
        }
        LOG.info("Heft: {} items extruded, {} left flat (no own sprite to repaint)", extruded, left);
    }

    /** effects: returns whether {@code block}, placed, is drawn crossed: a bush or a coral, but not what lies flat (a lily pad, petals) */
    private static boolean growsCrossed(net.minecraft.world.level.block.Block block) {
        return (block instanceof net.minecraft.world.level.block.BushBlock || block instanceof net.minecraft.world.level.block.BaseCoralPlantTypeBlock)
                && !(block instanceof net.minecraft.world.level.block.WaterlilyBlock) && !(block instanceof net.minecraft.world.level.block.PinkPetalsBlock);
    }

    /** Flat models met at draw time, extruded, until the next bake (D-0007). */
    private static volatile ExtrudedModel.Cache atDraw = new ExtrudedModel.Cache(name -> null);

    /** What Heft drew in place of what it was handed, for an item in a view (D-0007). */
    public enum Decision {
        /** One of Heft's own models (solid, extruded at bake, crossed). */
        HEFT,
        /** A flat model another mod chose at draw time, extruded from its repainted sprite. */
        EXTRUDED,
        /** A 3D model another mod or pack chose, left as it is. */
        LEFT_3D,
        /** Drawn by code of its own (a block entity renderer), left. */
        CUSTOM,
        /** A flat model left flat: the settings deny it, or no repainted sheet exists for its sprite. */
        FLAT
    }

    private static final java.util.Map<String, Decision> DECISIONS = new java.util.concurrent.ConcurrentHashMap<>();
    /** Item and view pairs already traced (-Dheft.traceDraw), each once per bake. */
    private static final java.util.Set<String> TRACED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Item and view pairs already logged as left flat for want of a sheet, so each is logged once per bake. */
    private static final java.util.Set<String> UNSHEETED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** effects: returns what Heft last drew for {@code item} in {@code view}, or null if it has not been drawn there since the last bake */
    @org.jetbrains.annotations.Nullable
    public static Decision decided(ResourceLocation item, net.minecraft.world.item.ItemDisplayContext view) {
        return DECISIONS.get(item + "|" + view.getSerializedName());
    }

    /**
     * effects: returns the model to draw for {@code stack} in {@code view} in place of {@code model}, which another mod
     * may have chosen at draw time: a flat one extruded from its repainted sprite (if it has one and the settings allow
     * it), anything else as it is; notes what it decided (D-0007)
     */
    public static BakedModel atDraw(ItemStack stack, net.minecraft.world.item.ItemDisplayContext view, BakedModel model) {
        if (stack.isEmpty()) {
            return model;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Decision d;
        BakedModel out = model;
        if (model instanceof SolidModel || model instanceof ExtrudedModel || model instanceof CrossedModel) {
            d = Decision.HEFT;
        } else if (model.isCustomRenderer()) {
            d = Decision.CUSTOM;
        } else if (model.isGui3d()) {
            d = Decision.LEFT_3D;
        } else if (!HeftConfig.extruded(id)) {
            d = Decision.FLAT;
        } else {
            out = atDraw.apply(model);
            d = out instanceof ExtrudedModel ? Decision.EXTRUDED : Decision.FLAT;
            if (d == Decision.FLAT && UNSHEETED.add(id + "|" + view.getSerializedName())) {
                java.util.Set<String> sprites = new java.util.TreeSet<>();
                model.getQuads(null, null, net.minecraft.util.RandomSource.create(42L)).forEach(q -> sprites.add(q.getSprite().contents().name().toString()));
                LOG.info("Heft: {} in {} is drawn flat by another mod's choice; no repainted sheet for {}", id, view.getSerializedName(), sprites);
            }
        }
        DECISIONS.put(id + "|" + view.getSerializedName(), d);
        if (Boolean.getBoolean("heft.traceDraw") && TRACED.add(id + "|" + view.getSerializedName())) {
            LOG.info("Heft: {} in {} handed {} -> {}", id, view.getSerializedName(), model.getClass().getName(), d);
        }
        return out;
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        // Draw-time extrusion looks its sheets up on Heft's atlas as stitched for this bake.
        var atlas = event.getModelManager().getAtlas(HeftAtlas.LOCATION);
        atDraw = new ExtrudedModel.Cache(atlas::getSprite);
        DECISIONS.clear();
        UNSHEETED.clear();
        TRACED.clear();
        Report.write(event.getModels(), lastRead);
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        InspectCommand.register(event.getDispatcher());
    }
}

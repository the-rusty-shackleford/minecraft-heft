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
    }

    @SubscribeEvent
    public static void onBaked(ModelEvent.BakingCompleted event) {
        Report.write(event.getModels(), lastRead);
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        InspectCommand.register(event.getDispatcher());
    }
}

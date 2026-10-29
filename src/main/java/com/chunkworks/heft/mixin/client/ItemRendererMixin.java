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
package com.chunkworks.heft.mixin.client;

import com.chunkworks.heft.client.HeftClient;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Whatever model is about to be drawn, flat is given depth (D-0007). Another mod may choose an item's model at
 * draw time -- Modefite from a pack's 1.21.4 item definition (Fresh Food, Refined Tools), VanillaBackport its
 * spawn eggs -- before {@code render} reaches its own drawing. So the swap is made where {@code render} sets
 * the chosen model's camera transforms, after every head injection has had its say: there Modefite's choice
 * is final, and changing it starts no recursion (Modefite re-renders an item on the ground whenever the model
 * it is handed is not its own, so a swap at the head looped until the stack overflowed). The extruded model
 * keeps the original's transforms, so it is drawn exactly where the original would have been.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    /**
     * The view an item is drawn in, noted around the whole of {@code render}: outside every other mod's injection, so a
     * mod that cancels {@code render} at its head to draw the item its own way (VanillaBackport's spawn eggs, through
     * Platform) still draws while the view is known (D-0010); with it, the level of detail by the item's distance.
     */
    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    private void heft$inView(ItemStack stack, ItemDisplayContext view, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light,
                             int overlay, BakedModel model, Operation<Void> original) {
        // How far the item is from the camera: the pose's translation, as the level renderer sets it (camera-relative).
        org.joml.Matrix4f m = pose.last().pose();
        float distanceSq = m.m30() * m.m30() + m.m31() * m.m31() + m.m32() * m.m32();
        HeftClient.inView(view, distanceSq, () -> original.call(stack, view, leftHand, pose, buffers, light, overlay, model));
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel heft$deepen(PoseStack pose, BakedModel model, ItemDisplayContext view, boolean leftHand, Operation<BakedModel> original,
                                   @Local(argsOnly = true) ItemStack stack) {
        // A GUI slot or a frame shows the item as the pack draws it, whoever chose the model (D-0010).
        if (HeftClient.flatView(view)) {
            return original.call(pose, HeftClient.flat(model), view, leftHand);
        }
        // An item Heft draws over a pack's own is swapped before the transforms are set, so they are Heft's (D-0008).
        return HeftClient.atDraw(stack, view, original.call(pose, HeftClient.beforeDraw(stack, model), view, leftHand));
    }
}

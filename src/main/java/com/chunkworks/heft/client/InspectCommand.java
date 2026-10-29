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

import com.mojang.brigadier.CommandDispatcher;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /heft inspect}: what Heft makes of the item in the main hand -- how it is drawn, its class and relief, the
 * sprite it is made from, its quads at each level of detail, what was last drawn in each view, and the Set It Down
 * tags it carries. A pose question becomes one command, not a squint (the handoff's §7.2).
 */
final class InspectCommand {
    private InspectCommand() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("heft").then(Commands.literal("inspect").executes(context -> {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
            for (String line : lines(stack)) {
                context.getSource().sendSuccess(() -> Component.literal(line), false);
            }
            return 1;
        })));
    }

    /** effects: returns the inspection of {@code stack}, a line each */
    static List<String> lines(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of("Heft: nothing in the main hand");
        }
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = HeftClient.beforeDraw(stack, mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0));
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String tags = stack.getTags().filter(t -> t.location().getNamespace().equals("setitdown"))
                .map(t -> "#" + t.location()).sorted().collect(Collectors.joining(" "));
        List<String> out = new java.util.ArrayList<>();
        out.add("Heft: " + id + " is drawn " + Report.kind(model).name().toLowerCase(Locale.ROOT).replace('_', ' ')
                + (HeftClient.drawnOverPacks().contains(id) ? ", over a pack's own model (D-0008)" : ""));
        String klass = HeftClient.classes().of(id);
        out.add("  class " + (klass == null ? "none" : klass) + ", relief "
                + (model instanceof LiveModel live ? java.util.Objects.toString(live.relief(), "none (extruded)") : "-"));
        if (model instanceof LiveModel live) {
            out.add("  from " + live.source().replace(" ", ", ") + " px; quads near " + live.quadCount(0) + ", mid " + live.quadCount(1)
                    + ", far " + live.quadCount(2));
        }
        StringBuilder drawn = new StringBuilder();
        for (ItemDisplayContext view : ItemDisplayContext.values()) {
            HeftClient.Decision d = HeftClient.decided(id, view);
            if (d != null) {
                drawn.append(drawn.length() == 0 ? "" : ", ").append(view.getSerializedName()).append(' ').append(d.name().toLowerCase(Locale.ROOT));
            }
        }
        out.add("  as last drawn: " + (drawn.length() == 0 ? "not yet" : drawn));
        out.add("  Set It Down tags: " + (tags.isEmpty() ? "none" : tags));
        return out;
    }
}

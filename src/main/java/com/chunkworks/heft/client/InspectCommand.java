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

import com.chunkworks.heft.domain.Box;
import com.chunkworks.heft.domain.Canon;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /heft inspect}: what Heft makes of the item in the main hand -- how it is drawn, its class,
 * material and size, its shape's box as built and as set down or framed, the canon's verdict, and the
 * Set It Down tags it carries. A pose question becomes one command, not a squint (the handoff's §7.2).
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
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0);
        Report.Kind kind = Report.kind(model);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String tags = stack.getTags().filter(t -> t.location().getNamespace().equals("setitdown"))
                .map(t -> "#" + t.location()).sorted().collect(Collectors.joining(" "));
        List<String> out = new java.util.ArrayList<>();
        out.add("Heft: " + id + " is drawn " + kind.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (model instanceof SolidModel solid) {
            Solid s = solid.solid();
            out.add("  class " + s.klass() + ", material " + s.material() + ", size " + (s.size() == null ? "normal" : s.size()));
            out.add("  built " + dims(s.shape().box()) + ", set down / framed " + dims(s.shape().rested())
                    + (Canon.topOut(s.shape().fixed()) ? ", top out" : ", top not out"));
            List<String> breaches = Canon.breaches(s.shape());
            out.add("  canon: " + (breaches.isEmpty() ? "kept" : String.join("; ", breaches)));
            solid.missing().forEach(m -> out.add("  no sprite " + m));
        }
        StringBuilder drawn = new StringBuilder();
        for (ItemDisplayContext view : ItemDisplayContext.values()) {
            HeftClient.Decision d = HeftClient.decided(BuiltInRegistries.ITEM.getKey(stack.getItem()), view);
            if (d != null) {
                drawn.append(drawn.length() == 0 ? "" : ", ").append(view.getSerializedName()).append(' ').append(d.name().toLowerCase(Locale.ROOT));
            }
        }
        out.add("  as last drawn: " + (drawn.length() == 0 ? "not yet" : drawn));
        out.add("  Set It Down tags: " + (tags.isEmpty() ? "none" : tags));
        return out;
    }

    private static String dims(Box b) {
        double[] s = b.size();
        return String.format(Locale.ROOT, "%.2f x %.2f x %.2f", s[0], s[1], s[2]);
    }
}

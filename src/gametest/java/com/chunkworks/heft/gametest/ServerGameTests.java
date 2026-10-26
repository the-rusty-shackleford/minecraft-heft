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

import com.chunkworks.heft.Heft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Heft on a dedicated server: everything it draws is the client's, so on a server it must load without
 * reaching for a single client class (a server would crash at start), and carry only its data.
 *
 * <p>Partitions: the mod loaded on a dedicated server; Set It Down's tags as Heft's classes give them (D-0006): a
 * pile class (an ingot, a book, a steak), a cluster class (a raw chunk, a dust), a size (tiny, small); an item no
 * class covers in none of Heft's; a tag entry for a mod the server lacks skipped, the tag still loading.
 */
@GameTestHolder(Heft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerGameTests {
    public ServerGameTests() {}

    private static final String TEMPLATE = "plot";

    private static net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag(String path) {
        return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("setitdown", path));
    }

    @GameTest(template = TEMPLATE)
    public void setItDownsTagsFollowTheClasses(GameTestHelper helper) {
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT).is(tag("stackable_pile")), "an ingot piles");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK).is(tag("stackable_pile")), "a book piles");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF).is(tag("stackable_pile")), "a steak piles");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON).is(tag("stackable_cluster")), "a raw chunk clusters");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GUNPOWDER).is(tag("stackable_cluster")), "a dust clusters");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RED_DYE).is(tag("size/tiny")), "a dye is tiny");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_SIGN).is(tag("size/small")) == false, "a sign is not small");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK).is(tag("size/small")), "a rod is small");
        helper.assertTrue(!new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD).is(tag("stackable_pile"))
                && !new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD).is(tag("stackable_cluster")), "a sword is in neither");
        // Create's brass ingot is in the pile tag's file; Create is not on this server, and the tag loads all the same.
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag("stackable_pile")).isPresent(), "the pile tag loaded");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public void heftLoadsOnADedicatedServer(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(Heft.MOD_ID), "Heft is loaded");
        helper.succeed();
    }
}

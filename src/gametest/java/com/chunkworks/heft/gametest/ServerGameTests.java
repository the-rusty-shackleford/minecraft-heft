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
 * <p>Partitions: the mod loaded on a dedicated server.
 */
@GameTestHolder(Heft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerGameTests {
    public ServerGameTests() {}

    private static final String TEMPLATE = "plot";

    @GameTest(template = TEMPLATE)
    public void heftLoadsOnADedicatedServer(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(Heft.MOD_ID), "Heft is loaded");
        helper.succeed();
    }
}

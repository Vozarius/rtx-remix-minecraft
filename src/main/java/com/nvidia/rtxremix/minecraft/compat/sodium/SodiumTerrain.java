/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

public final class SodiumTerrain {
    private SodiumTerrain() {}
    public static LongSet built() {
        var renderer = SodiumWorldRenderer.instanceNullable();
        return renderer == null ? LongSets.emptySet() : ((SodiumTerrainAccess) renderer).rtxremix$builtSections();
    }
    public static LongSet recapture(LongSet sections) {
        var renderer = SodiumWorldRenderer.instanceNullable();
        return renderer == null ? LongSets.emptySet() : ((SodiumTerrainAccess) renderer).rtxremix$recaptureSections(sections);
    }
    public static void rebuild(int x, int y, int z) {
        var renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer != null) renderer.scheduleRebuildForChunk(x, y, z, true);
    }
}

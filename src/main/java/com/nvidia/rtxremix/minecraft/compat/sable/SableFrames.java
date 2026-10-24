/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sable;

import com.nvidia.rtxremix.minecraft.compat.SpatialFrame;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.world.ClientWorld;

public final class SableFrames {
    private SableFrames() {}

    public static SpatialFrame find(ClientWorld world, int chunkX, int chunkZ) {
        var container = SubLevelContainer.getContainer(world);
        var plot = container == null ? null : container.getPlot(chunkX, chunkZ);
        return plot == null ? null : (SpatialFrame) plot.getSubLevel();
    }
    public static void rebuild(ClientWorld world) {
        var container = SubLevelContainer.getContainer(world);
        if (container != null) {
            for (var sublevel : container.getAllSubLevels()) sublevel.updateRenderData();
        }
    }

}

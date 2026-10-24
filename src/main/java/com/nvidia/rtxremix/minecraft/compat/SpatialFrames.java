/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.minecraft.compat.sable.SableFrames;
import net.minecraft.client.world.ClientWorld;

/** Keeps optional sublevel APIs outside the ordinary terrain and entity paths. */
public final class SpatialFrames {
    private static final boolean SABLE = SpatialFrames.class.getClassLoader()
            .getResource("dev/ryanhcode/sable/api/sublevel/SubLevelContainer.class") != null;

    private SpatialFrames() {}

    public static SpatialFrame find(ClientWorld world, int chunkX, int chunkZ) {
        return SABLE && world != null ? SableFrames.find(world, chunkX, chunkZ) : null;
    }
    public static void rebuild(ClientWorld world) {
        if (SABLE && world != null) SableFrames.rebuild(world);
    }

}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.minecraft.light.LightManager;
import net.minecraft.client.world.ClientWorld;

/** Optional Create integration is linked only when its entity API is present. */
public final class MovingBlockLighting {
    private static final boolean PRESENT =
            MovingBlockLighting.class.getClassLoader().getResource(
                    "com/simibubi/create/content/contraptions/AbstractContraptionEntity.class")
            != null;
    private MovingBlockLighting() {}
    public static void update(ClientWorld world, float tickDelta, LightManager lights) {
        if (PRESENT)
            com.nvidia.rtxremix.minecraft.compat.create.ContraptionLighting.update(world, tickDelta, lights);
    }
    public static void clear() {
        if (PRESENT)
            com.nvidia.rtxremix.minecraft.compat.create.ContraptionLighting.clear();
    }
}

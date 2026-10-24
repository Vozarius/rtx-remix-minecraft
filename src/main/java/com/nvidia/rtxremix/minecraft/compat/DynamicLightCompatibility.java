/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.RemixAPI;
import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumDynamicLightBridge;
import net.minecraft.client.world.ClientWorld;

public final class DynamicLightCompatibility {
    private static final boolean PRESENT = DynamicLightCompatibility.class.getClassLoader()
            .getResource("toni/sodiumdynamiclights/SodiumDynamicLights.class") != null;
    private DynamicLightCompatibility() {}
    public static void update(ClientWorld world, RemixAPI api) { if (PRESENT) SodiumDynamicLightBridge.update(world, api); }
    public static void clear() { if (PRESENT) SodiumDynamicLightBridge.clear(); }
}

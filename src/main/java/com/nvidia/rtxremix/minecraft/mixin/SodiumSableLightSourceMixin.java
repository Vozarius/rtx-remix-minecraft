/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.compat.SpatialFrames;
import com.nvidia.rtxremix.minecraft.compat.sodium.CapturedTerrainLightSource;
import com.nvidia.rtxremix.minecraft.light.BlockLightRuleManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import toni.sodiumdynamiclights.DynamicLightSource;

@Pseudo
@Mixin(targets = "top.leonx.dynlight.sodium.sable.SableDynLightSource", remap = false)
public abstract class SodiumSableLightSourceMixin implements CapturedTerrainLightSource {
    @Shadow @Final private BlockPos plotPos;

    @Override
    public boolean rtxremix$hasCapturedTerrainLight() {
        var level = ((DynamicLightSource)(Object)this).sdl$getDynamicLightLevel();
        return level instanceof ClientWorld world
                && SpatialFrames.find(world, plotPos.getX() >> 4, plotPos.getZ() >> 4) != null
                && BlockLightRuleManager.hasMatchingRules(world.getBlockState(plotPos));
    }
}

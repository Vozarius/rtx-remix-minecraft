/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumLightSources;
import toni.sodiumdynamiclights.DynamicLightSource;
import toni.sodiumdynamiclights.SodiumDynamicLights;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(value = SodiumDynamicLights.class, remap = false)
public abstract class SodiumDynamicLightsMixin implements SodiumLightSources {
    @Shadow @Final private Set<DynamicLightSource> dynamicLightSources;
    @Shadow @Final private ReentrantReadWriteLock lightSourcesLock;

    @Override
    public DynamicLightSource[] rtxremix$copyLightSources(DynamicLightSource[] reusable) {
        lightSourcesLock.readLock().lock();
        try { return dynamicLightSources.toArray(reusable); }
        finally { lightSourcesLock.readLock().unlock(); }
    }
}

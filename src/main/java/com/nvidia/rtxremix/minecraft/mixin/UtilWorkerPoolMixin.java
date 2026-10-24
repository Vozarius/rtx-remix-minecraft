/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.WorkerPoolCap;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the worker cap before Util creates its shared executors. */
@Mixin(Util.class)
public abstract class UtilWorkerPoolMixin {
    @Inject(method = "<clinit>", at = @At("HEAD"))
    private static void rtxremix$capWorkers(CallbackInfo ci) {
        WorkerPoolCap.apply();
    }
}

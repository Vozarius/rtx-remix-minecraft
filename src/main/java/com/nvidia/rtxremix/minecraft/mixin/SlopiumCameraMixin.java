/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Camera occlusion cannot decide which models a path tracer needs for shadows and reflections. */
@Pseudo
@Mixin(targets = "dev.slopium.visibility.CreateWallControl", remap = false)
public abstract class SlopiumCameraMixin {
    @Inject(method = {"hidden", "outsideCamera"}, at = @At("HEAD"), cancellable = true)
    private static void rtxremix$keepTracedModels(CallbackInfoReturnable<Boolean> cir) {
        if (RtxRemixMod.isRemixWorldRendering())
            cir.setReturnValue(false);
    }
}

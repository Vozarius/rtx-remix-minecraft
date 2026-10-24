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

/** Routes Flywheel users through their block/entity renderers while Remix captures them. */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.BackendManagerImpl", remap = false)
public abstract class FlywheelBackendMixin {
    @Inject(method = "isBackendOn", at = @At("HEAD"), cancellable = true)
    private static void rtxremix$useCapturedRenderers(CallbackInfoReturnable<Boolean> cir) {
        if (RtxRemixMod.isRtxEnabled()) cir.setReturnValue(false);
    }
}

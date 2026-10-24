/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = VanillaSubLevelRenderDispatcher.class, remap = false)
public abstract class SableRenderDispatcherMixin {
    // Both dispatcher implementations use this predicate, including resize().
    @Inject(method = "isSingleBlock", at = @At("HEAD"), cancellable = true)
    private static void rtxremix$captureThroughSectionBuilder(ClientSubLevel sublevel, CallbackInfoReturnable<Boolean> cir) {
        if (RtxRemixMod.isRtxEnabled()) cir.setReturnValue(false);
    }
}

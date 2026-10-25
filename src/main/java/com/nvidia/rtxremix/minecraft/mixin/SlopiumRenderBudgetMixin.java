/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remix already budgets its offscreen model sweep; don't turn an accepted capture into an empty mesh. */
@Pseudo
@Mixin(targets = "dev.slopium.visibility.RenderBudget", remap = false)
public abstract class SlopiumRenderBudgetMixin {
    @Inject(method = "outside", at = @At("HEAD"), cancellable = true)
    private static void rtxremix$captureBudget(CallbackInfoReturnable<Boolean> cir) {
        if (EntityRenderContext.get().isRemixPass()
                || com.nvidia.rtxremix.minecraft.compat.BlockEntityCaptureScope.active())
            cir.setReturnValue(false);
    }
}

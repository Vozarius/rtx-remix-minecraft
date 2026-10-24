/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import net.minecraft.entity.Entity;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raster occlusion cannot remove geometry needed by traced shadows and reflections. */
@Mixin(value = {Entity.class, BlockEntity.class}, priority = 2000)
public abstract class EntityCullingVisibilityMixin {
    @Inject(method = {"isCulled", "isOutOfCamera"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void rtxremix$keepTraceableGeometry(CallbackInfoReturnable<Boolean> cir) {
        if (RtxRemixMod.isRtxEnabled()) cir.setReturnValue(false);
    }
}

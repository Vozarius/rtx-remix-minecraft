/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;
import com.nvidia.rtxremix.minecraft.compat.bits.HeadlampLighting;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BlockEntity.class)
public abstract class HeadlampLifecycleMixin {
    @Inject(method = {"setWorld", "cancelRemoval"}, at = @At("RETURN"))
    private void rtxremix$track(CallbackInfo ci) {
        HeadlampLighting.track((BlockEntity) (Object) this);
    }
    @Inject(method = "markRemoved", at = @At("HEAD"))
    private void rtxremix$forget(CallbackInfo ci) {
        HeadlampLighting.forget((BlockEntity) (Object) this);
    }
}

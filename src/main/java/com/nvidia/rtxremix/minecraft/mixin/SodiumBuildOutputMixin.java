/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumCapturedOutput;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockDataQueue;
import com.nvidia.rtxremix.minecraft.terrain.capture.SectionBlockData;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = ChunkBuildOutput.class, remap = false)
public abstract class SodiumBuildOutputMixin implements SodiumCapturedOutput {
    @Unique private SectionBlockData rtxremix$data;
    @Unique private BlockDataQueue.CaptureToken rtxremix$token;

    @Override
    public void rtxremix$setCapture(SectionBlockData data, BlockDataQueue.CaptureToken token) {
        rtxremix$data = data;
        rtxremix$token = token;
    }

    @Override
    public boolean rtxremix$publishCapture() {
        if (rtxremix$data == null) return false;
        SectionBlockData data = rtxremix$data;
        rtxremix$data = null;
        BlockDataQueue.enqueue(data, rtxremix$token);
        rtxremix$token = null;
        return true;
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void rtxremix$release(CallbackInfo ci) {
        if (rtxremix$data != null) {
            rtxremix$data.meshData().release();
            rtxremix$data = null;
            rtxremix$token = null;
        }
    }
}

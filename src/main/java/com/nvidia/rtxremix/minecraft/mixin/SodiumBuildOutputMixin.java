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

    // Empty Sodium builds skip the worker entirely. Stamp their submission here,
    // rather than at upload, which can occur after a newer worker has started.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void rtxremix$emptyCapture(CallbackInfo ci) {
        var output = (ChunkBuildOutput) (Object) this;
        if (com.nvidia.rtxremix.minecraft.RtxRemixMod.isRtxEnabled() && output.meshes.isEmpty()) {
            var render = output.render;
            var section = net.minecraft.util.math.ChunkSectionPos.from(
                    render.getChunkX(), render.getChunkY(), render.getChunkZ());
            rtxremix$setCapture(com.nvidia.rtxremix.minecraft.compat.sodium.SodiumSectionCapture.emptySection(
                                        section.getX(), section.getY(), section.getZ()),
                    BlockDataQueue.beginSectionCapture(section));
        }
    }

    @Override
    public void rtxremix$setCapture(SectionBlockData data, BlockDataQueue.CaptureToken token) {
        if (rtxremix$data != null)
            rtxremix$data.meshData().release();
        rtxremix$data = data;
        rtxremix$token = token;
    }

    @Override
    public boolean rtxremix$publishCapture() {
        if (rtxremix$data == null)
            return false;
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

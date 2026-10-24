/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nvidia.rtxremix.minecraft.compat.framegen.FrameGenBridge;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "dev.screamix.framegen.client.FrameGenRuntime", remap = false)
public abstract class FrameGenRuntimeMixin {
    @WrapMethod(method = "captureFrame")
    private static void rtxremix$captureFinalImage(Camera camera, Matrix4f view, Matrix4f projection, Operation<Void> original) {
        if (!FrameGenBridge.defer(camera, view, projection)) original.call(camera, view, projection);
    }
    @ModifyExpressionValue(method = "tryGenerateFrame",
            at = @At(value = "INVOKE",
                    target = "Ldev/screamix/framegen/client/IrisCompat;isShaderPackActive()Z"))
    private static boolean rtxremix$keepCapturedHandNearCamera(boolean shaderPack) {
        return shaderPack || RtxRemixMod.isRtxEnabled();
    }

}

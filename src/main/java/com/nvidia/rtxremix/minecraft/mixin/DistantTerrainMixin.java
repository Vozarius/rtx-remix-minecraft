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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** This forwarding method owns no GL state; the surrounding DH pass still restores its framebuffer/state. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.renderer.LodRenderer", remap = false)
public abstract class DistantTerrainMixin {
    @Inject(method = "renderTerrain(Lcom/seibel/distanthorizons/core/wrapperInterfaces/render/renderPass/"
                     + "IDhTerrainRenderer;"
                    + "Lcom/seibel/distanthorizons/core/render/RenderBufferHandler;"
                    + "Lcom/seibel/distanthorizons/core/render/RenderParams;Z"
                    + "Lcom/seibel/distanthorizons/core/wrapperInterfaces/minecraft/IProfilerWrapper;)V",
            at = @At("HEAD"), cancellable = true)
    private void
    rtxremix$replaceDraw(CallbackInfo ci) {
        if (RtxRemixMod.isRemixWorldRendering())
            ci.cancel();
    }
}

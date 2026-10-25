/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.compat.dh.*;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.QuadTree.LodRenderSection;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = RenderBufferHandler.class, remap = false)
public abstract class DistantSelectionMixin {
    @Shadow @Final private ArrayList<LodRenderSection> tempProcessNodeList;
    @Inject(method = "buildRenderList", at = @At("RETURN"))
    private void rtxremix$select(CallbackInfo ci) {
        if (!RtxRemixMod.isRemixWorldRendering())
            return;
        for (int i = 0; i < tempProcessNodeList.size(); i++) {
            var section = tempProcessNodeList.get(i);
            if (section != null && section.getRenderingEnabled() && section.renderBufferContainer != null)
                DistantScene.select(((DistantBufferAccess) section.renderBufferContainer).rtxremix$data());
        }
    }
}

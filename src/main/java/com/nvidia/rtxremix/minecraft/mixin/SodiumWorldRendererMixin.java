/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import org.spongepowered.asm.mixin.Shadow;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumTerrainAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class SodiumWorldRendererMixin implements SodiumTerrainAccess {
    @Shadow private RenderSectionManager renderSectionManager;
    // The dispatcher has no GL side effects of its own; pass setup belongs to renderLayer().
    @Inject(method = "drawChunkLayer", at = @At("HEAD"), cancellable = true)
    private void rtxremix$replaceTerrainDraw(CallbackInfo ci) {
        if (RtxRemixMod.isRemixWorldRendering()) ci.cancel();
    }
    @Override public LongSet rtxremix$builtSections() {
        return renderSectionManager == null ? LongSets.emptySet()
                : ((SodiumTerrainAccess)renderSectionManager).rtxremix$builtSections();
    }
    @Override public LongSet rtxremix$recaptureSections(LongSet sections) {
        return renderSectionManager == null ? LongSets.emptySet()
                : ((SodiumTerrainAccess)renderSectionManager).rtxremix$recaptureSections(sections);
    }

}

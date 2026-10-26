/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Material and connected-texture changes can keep exactly the same block state. */
@Pseudo
@Mixin(targets = "com.copycatsplus.copycats.utility.BlockEntityUtils", remap = false)
public abstract class CopycatUpdatesMixin {
    @Inject(method = "redraw", at = @At("RETURN"))
    private static void rtxremix$materialChanged(BlockEntity blockEntity, CallbackInfo ci) {
        if (!RtxRemixMod.isRtxEnabled() || !(blockEntity.getWorld() instanceof ClientWorld level))
            return;
        var client = MinecraftClient.getInstance();
        var session = client.world;
        var pos = blockEntity.getPos();
        client.execute(() -> {
            if (RtxRemixMod.isRtxEnabled() && client.world == session && blockEntity.getWorld() == level
                    && !blockEntity.isRemoved()) {
                level.scheduleBlockRenders(ChunkSectionPos.getSectionCoord(pos.getX()),
                        ChunkSectionPos.getSectionCoord(pos.getY()),
                        ChunkSectionPos.getSectionCoord(pos.getZ()));
            }
        });
    }
}

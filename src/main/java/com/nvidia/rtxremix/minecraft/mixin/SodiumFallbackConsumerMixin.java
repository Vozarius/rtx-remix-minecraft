/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumSectionCapture;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkVertexConsumer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = ChunkVertexConsumer.class, remap = false)
public abstract class SodiumFallbackConsumerMixin {
    @Shadow @Final private ChunkVertexEncoder.Vertex[] vertices;
    @Shadow private Material material;

    @Inject(method = "calculateNormal", at = @At("HEAD"))
    private void rtxremix$quad(CallbackInfoReturnable<Integer> cir) {
        SodiumSectionCapture.current().quad(vertices, material);
    }
}

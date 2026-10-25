/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.compat.BlockEntityCaptureScope;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SlopiumSodiumScopeMixin {
    @WrapMethod(method = "renderBlockEntities(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/"
                         + "client/render/BufferBuilderStorage;Lit/unimi/dsi/fastutil/longs/"
                         + "Long2ObjectMap;Lnet/minecraft/client/render/Camera;FLcom/llamalad7/mixinextras/"
                         + "sugar/ref/LocalBooleanRef;)V")
    private void
    rtxremix$scope(MatrixStack matrices, BufferBuilderStorage buffers, Long2ObjectMap<?> breaking,
            Camera camera, float delta, LocalBooleanRef glowing, Operation<Void> original) {
        if (!RtxRemixMod.isRemixWorldRendering()) {
            original.call(matrices, buffers, breaking, camera, delta, glowing);
            return;
        }
        BlockEntityCaptureScope.enter();
        try {
            original.call(matrices, buffers, breaking, camera, delta, glowing);
        } finally {
            BlockEntityCaptureScope.exit();
        }
    }
}

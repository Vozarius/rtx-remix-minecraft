/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.gl.DynamicBufferUpload;
import net.minecraft.client.gl.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

/**
 * Routes the {@code glBufferData} re-specification of DYNAMIC buffers through
 * {@link DynamicBufferUpload}. STATIC uploads keep the original call.
 */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin {
    private static final String rtxremix$GL_BUFFER_DATA =
        "Lcom/mojang/blaze3d/systems/RenderSystem;glBufferData(ILjava/nio/ByteBuffer;I)V";

    private final DynamicBufferUpload.Storage rtxremix$vertexStorage = new DynamicBufferUpload.Storage();
    private final DynamicBufferUpload.Storage rtxremix$indexStorage = new DynamicBufferUpload.Storage();

    @WrapOperation(method = "uploadVertexBuffer", at = @At(value = "INVOKE", target = rtxremix$GL_BUFFER_DATA))
    private void rtxremix$uploadVertexData(int target, ByteBuffer data, int usage, Operation<Void> original) {
        rtxremix$upload(target, data, usage, rtxremix$vertexStorage, original);
    }

    // Explicit descriptors keep both overloads selected after production remapping.
    @WrapOperation(
        method = {
            "uploadIndexBuffer(Lnet/minecraft/client/util/BufferAllocator$CloseableBuffer;)V",
            "uploadIndexBuffer(Lnet/minecraft/client/render/BuiltBuffer$DrawParameters;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/systems/RenderSystem$ShapeIndexBuffer;"
        },
        at = @At(value = "INVOKE", target = rtxremix$GL_BUFFER_DATA))
    private void rtxremix$uploadIndexData(int target, ByteBuffer data, int usage, Operation<Void> original) {
        rtxremix$upload(target, data, usage, rtxremix$indexStorage, original);
    }

    private static void rtxremix$upload(int target, ByteBuffer data, int usage,
                                      DynamicBufferUpload.Storage storage, Operation<Void> original) {
        if (DynamicBufferUpload.handles(usage)) {
            DynamicBufferUpload.upload(target, data, usage, storage);
        } else {
            original.call(target, data, usage);
        }
    }
}

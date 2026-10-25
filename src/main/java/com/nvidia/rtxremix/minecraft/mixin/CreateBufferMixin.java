/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.compat.create.CreateBufferCapture;
import com.nvidia.rtxremix.minecraft.entity.RemixCapturingConsumer;
import net.createmod.catnip.render.TemplateMesh;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.createmod.catnip.render.ShadeSeparatingSuperByteBuffer", remap = false)
public abstract class CreateBufferMixin {
    @Shadow @Final private TemplateMesh template;
    @Shadow @Final private MatrixStack transforms;
    @Shadow private float r, g, b, a;
    @Shadow private SuperByteBuffer.SpriteShiftFunc spriteShiftFunc;
    @Unique private final Matrix4f rtxremix$pose = new Matrix4f();
    @Unique private final SuperByteBuffer.ShiftOutput rtxremix$shift = new SuperByteBuffer.ShiftOutput();

    @Inject(method = "renderInto", at = @At("HEAD"), cancellable = true)
    private void rtxremix$capture(MatrixStack input, VertexConsumer builder, CallbackInfo ci) {
        if (!(builder instanceof RemixCapturingConsumer consumer))
            return;
        rtxremix$pose.set(input.peek().getPositionMatrix()).mul(transforms.peek().getPositionMatrix());
        try {
            CreateBufferCapture.emit(
                    template, consumer, rtxremix$pose, r, g, b, a, spriteShiftFunc, rtxremix$shift);
        } finally {
            ((SuperByteBuffer) (Object) this).reset();
        }
        ci.cancel();
    }
}

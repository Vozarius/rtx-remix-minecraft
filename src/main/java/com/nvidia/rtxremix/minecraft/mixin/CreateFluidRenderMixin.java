/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nvidia.rtxremix.minecraft.texture.FluidCaptureContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "net.createmod.catnip.render.FluidRenderHelper", remap = false)
public abstract class CreateFluidRenderMixin {
    @WrapMethod(method = "renderFluidBox(Ljava/lang/Object;FFFFFFLnet/minecraft/client/render/"
                    + "VertexConsumer;Lnet/minecraft/client/util/math/MatrixStack;IZZ)V")
    private void
    rtxremix$stack(Object raw, float x0, float y0, float z0, float x1, float y1, float z1,
            VertexConsumer consumer, MatrixStack matrices, int light, boolean bottom, boolean top,
            Operation<Void> original) {
        var previous = FluidCaptureContext.get();
        if (raw instanceof FluidStack fluid)
            FluidCaptureContext.set(fluid.getFluid());
        try {
            original.call(raw, x0, y0, z0, x1, y1, z1, consumer, matrices, light, bottom, top);
        } finally {
            FluidCaptureContext.set(previous);
        }
    }
    @WrapMethod(method = "renderFluidBox(Lnet/minecraft/fluid/FluidState;FFFFFFLnet/minecraft/client/render/"
                    + "VertexConsumer;Lnet/minecraft/client/util/math/MatrixStack;IZZ)V")
    private void
    rtxremix$state(FluidState fluid, float x0, float y0, float z0, float x1, float y1, float z1,
            VertexConsumer consumer, MatrixStack matrices, int light, boolean bottom, boolean top,
            Operation<Void> original) {
        var previous = FluidCaptureContext.set(fluid.getFluid());
        try {
            original.call(fluid, x0, y0, z0, x1, y1, z1, consumer, matrices, light, bottom, top);
        } finally {
            FluidCaptureContext.set(previous);
        }
    }
}

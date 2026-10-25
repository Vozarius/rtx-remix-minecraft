/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;
import net.minecraft.fluid.Fluid;
/** Fluid identity survives shared sprites (water/lava used by several mod fluid types). */
public final class FluidCaptureContext {
    private static final ThreadLocal<Fluid> CURRENT = new ThreadLocal<>();
    private FluidCaptureContext() {}
    public static Fluid get() {
        return CURRENT.get();
    }
    public static Fluid set(Fluid fluid) {
        Fluid previous = CURRENT.get();
        if (fluid == null)
            CURRENT.remove();
        else
            CURRENT.set(fluid);
        return previous;
    }
}

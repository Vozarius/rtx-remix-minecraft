/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

/** Resolves each surface independently of the render layer shared by the rest of a window model. */
public final class BlockSurfaceLayer {
    private BlockSurfaceLayer() {}
    public static String resolve(
            String requested, boolean glass, boolean transparency, boolean fractionalAlpha) {
        if (glass)
            return "glass";
        if (fractionalAlpha)
            return "translucent";
        if (transparency)
            return "cutout";
        return "glass".equals(requested) ? "solid" : requested;
    }
}

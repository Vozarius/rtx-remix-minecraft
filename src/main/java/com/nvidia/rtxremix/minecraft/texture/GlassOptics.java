/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

/** Colour-selective absorption; achromatic frame pixels leave a clear dielectric underneath. */
public final class GlassOptics {
    private GlassOptics() {}
    public static void transmission(byte[] albedo, int source, byte[] out, int dest) {
        int r = albedo[source] & 255, g = albedo[source + 1] & 255, b = albedo[source + 2] & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        for (int c = 0; c < 3; c++) {
            int alpha = albedo[source + 3] & 255;
            float value = alpha == 0 || alpha == 255 || max == 0 || max - min < 12
                    ? 1
                    : (albedo[source + c] & 255) / (float) max;
            out[dest + c] = (byte) Math.round(Math.max(.02, Math.pow(value, 2.2)) * 255);
        }
        out[dest + 3] = (byte) 255;
    }
}

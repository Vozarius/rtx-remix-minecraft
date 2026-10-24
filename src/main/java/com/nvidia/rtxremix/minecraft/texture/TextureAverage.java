/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

/** Alpha-weighted sRGB colour and mean coverage of a texture, including transparent pixels. */
public record TextureAverage(float red, float green, float blue, float alpha) {
    public static TextureAverage of(byte[] rgba) {
        double r = 0, g = 0, b = 0, coverage = 0;
        for (int i = 0; i < rgba.length; i += 4) {
            int a = rgba[i + 3] & 255;
            r += (rgba[i] & 255) * (double) a;
            g += (rgba[i + 1] & 255) * (double) a;
            b += (rgba[i + 2] & 255) * (double) a;
            coverage += a;
        }
        if (coverage == 0)
            return new TextureAverage(0, 0, 0, 0);
        return new TextureAverage((float) (r / coverage / 255), (float) (g / coverage / 255),
                (float) (b / coverage / 255), (float) (coverage / (rgba.length / 4.0) / 255));
    }
}

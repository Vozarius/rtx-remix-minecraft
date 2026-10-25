/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

/** Tileable Sobel normals and luminance height. Transparent neighbours do not create silhouette ridges. */
public final class ProceduralSurfaceMaps {
    private ProceduralSurfaceMaps() {}
    public record Maps(byte[] normal, byte[] height) {}
    public static Maps generate(byte[] rgba, int width, int height, float strength, float depth) {
        if (rgba.length != width * height * 4)
            throw new IllegalArgumentException("RGBA dimensions");
        float[] elevation = new float[width * height];
        for (int i = 0; i < elevation.length; i++)
            elevation[i] = ((rgba[i * 4] & 255) * .2126f + (rgba[i * 4 + 1] & 255) * .7152f
                                   + (rgba[i * 4 + 2] & 255) * .0722f)
                    / 255;
        byte[] normals = new byte[rgba.length], heights = new byte[width * height];
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                float dx = 0, dy = 0;
                for (int oy = -1; oy <= 1; oy++)
                    for (int ox = -1; ox <= 1; ox++) {
                        int sample = Math.floorMod(y + oy, height) * width + Math.floorMod(x + ox, width);
                        float value = (rgba[sample * 4 + 3] & 255) == 0 ? elevation[i] : elevation[sample];
                        dx += value * ox * (oy == 0 ? 2 : 1);
                        dy += value * oy * (ox == 0 ? 2 : 1);
                    }
                dx = -dx * strength / 4;
                dy = -dy * strength / 4;
                if ((rgba[i * 4 + 3] & 255) == 0) {
                    dx = 0;
                    dy = 0;
                }
                float length = (float) Math.sqrt(dx * dx + dy * dy + 1);
                normals[i * 4] = (byte) Math.round((dx / length * .5f + .5f) * 255);
                normals[i * 4 + 1] = (byte) Math.round((dy / length * .5f + .5f) * 255);
                normals[i * 4 + 2] = (byte) Math.round(255 / length);
                normals[i * 4 + 3] = (byte) 255;
                heights[i] = (byte) Math.round((1 - depth + elevation[i] * depth) * 255);
            }
        return new Maps(normals, heights);
    }
}

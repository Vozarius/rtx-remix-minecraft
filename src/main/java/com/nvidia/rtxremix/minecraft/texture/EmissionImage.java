/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import com.nvidia.rtxremix.minecraft.light.LightEntry;
import java.util.HashMap;
import java.util.Map;

/**
 * Reload-time emission sampling, cached by the texel rectangle a baked face uses.
 * Strength measures emitting texels, independently of the unused UV space around a small filament;
 * the material's emission texture itself still defines the emitting area for path tracing.
 */
final class EmissionImage {
    private final byte[] albedo, emission;
    private final int width, height;
    private final Map<Long, EmissionProfile> regions = new HashMap<>();

    EmissionImage(byte[] albedo, byte[] emission, int width, int height) {
        this.albedo = albedo;
        this.emission = emission;
        this.width = width;
        this.height = height;
    }

    EmissionProfile sample(float minU, float minV, float maxU, float maxV) {
        int x0 = lower(minU, width), y0 = lower(minV, height);
        int x1 = upper(maxU, width, x0), y1 = upper(maxV, height, y0);
        long key = (long) x0 << 48 | (long) y0 << 32 | (long) x1 << 16 | y1;
        return regions.computeIfAbsent(key, ignored -> measure(x0, y0, x1, y1));
    }

    private EmissionProfile measure(int x0, int y0, int x1, int y1) {
        double strength = 0, r = 0, g = 0, b = 0;
        int count = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int i = (y * width + x) * 4;
                if ((albedo[i + 3] & 255) == 0 || (emission[i + 3] & 255) == 0)
                    continue;
                int base = Math.max(albedo[i] & 255, Math.max(albedo[i + 1] & 255, albedo[i + 2] & 255));
                int er = emission[i] & 255, eg = emission[i + 1] & 255, eb = emission[i + 2] & 255;
                int glow = Math.max(er, Math.max(eg, eb));
                if (base == 0 || glow == 0)
                    continue;
                strength += (double) glow / base;
                count++;
                double w = er + eg + eb;
                r += er * w;
                g += eg * w;
                b += eb * w;
            }
        if (count == 0)
            return new EmissionProfile(0, LightEntry.ColorOverride.NONE);
        double peak = Math.max(r, Math.max(g, b));
        return new EmissionProfile((float) (strength / count),
                LightEntry.ColorOverride.of((float) (r / peak), (float) (g / peak), (float) (b / peak)));
    }

    private static int lower(float uv, int size) {
        return Math.max(0, Math.min(size - 1, (int) Math.floor(uv * size + 1e-4f)));
    }

    private static int upper(float uv, int size, int min) {
        return Math.max(min + 1, Math.min(size, (int) Math.ceil(uv * size - 1e-4f)));
    }
}

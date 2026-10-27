/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.util.Identifier;

/** Sprite-specific emitted colour; _e masks still control only emitting area and strength. */
public final class EmissionTint {
    public static final Identifier LIGHTBULB_ON = Identifier.of("bits_n_bobs", "block/lightbulb_on");
    private EmissionTint() {}
    public static void apply(Identifier sprite, byte[] rgba, int stride, int px, int py, int w, int h) {
        if (!LIGHTBULB_ON.equals(sprite))
            return;
        for (int y = py; y < py + h; y++) {
            for (int x = px; x < px + w; x++) {
                int i = (y * stride + x) * 4;
                rgba[i + 1] = (byte) Math.round((rgba[i + 1] & 255) * .76f);
                rgba[i + 2] = (byte) Math.round((rgba[i + 2] & 255) * .42f);
            }
        }
    }
    public static float[] lightbulbColor() {
        return new float[] {1, .76f, .42f};
    }
}

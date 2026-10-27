/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import java.util.BitSet;
import java.util.function.IntBinaryOperator;

/** Alpha classification over every animation frame, with a conservative local-texel transparency map. */
record AlphaProfile(BitSet transparent, boolean fractional) {
    static AlphaProfile scan(
            int width, int height, int frameWidth, int frameHeight, IntBinaryOperator pixel) {
        BitSet transparent = new BitSet(frameWidth * frameHeight);
        boolean fractional = false;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int alpha = pixel.applyAsInt(x, y) >>> 24;
                if (alpha < 255)
                    transparent.set((y % frameHeight) * frameWidth + x % frameWidth);
                fractional |= alpha > 0 && alpha < 255;
            }
        return new AlphaProfile(transparent, fractional);
    }
}

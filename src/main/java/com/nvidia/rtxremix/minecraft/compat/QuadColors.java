/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

public final class QuadColors {
    private QuadColors() {}

    public static int tintAbgr(int abgr, int tintArgb) {
        int r = (abgr & 255) * ((tintArgb >>> 16) & 255) / 255;
        int g = ((abgr >>> 8) & 255) * ((tintArgb >>> 8) & 255) / 255;
        int b = ((abgr >>> 16) & 255) * (tintArgb & 255) / 255;
        return (abgr & 0xff000000) | (r << 16) | (g << 8) | b;
    }
}

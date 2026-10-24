/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

/** Keeps repeated texture coordinates precise at far-away sublevel plot positions. */
public final class TiledTextureCoordinates {
    private TiledTextureCoordinates() {}

    public static float tile(int sectionBase, float local, float tileSize) {
        double base = sectionBase / (double)tileSize;
        return (float)(base - Math.floor(base) + local / (double)tileSize);
    }
}

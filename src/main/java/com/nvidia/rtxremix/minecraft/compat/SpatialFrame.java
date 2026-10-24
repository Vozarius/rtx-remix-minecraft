/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.Transform;

/** A moving local coordinate system. Its identity survives pose changes. */
public interface SpatialFrame {
    boolean rtxremix$isAlive();
    boolean rtxremix$isSectionEmpty(int chunkX, int sectionY, int chunkZ);
    void rtxremix$transform(double anchorX, double anchorY, double anchorZ, Transform destination);
}

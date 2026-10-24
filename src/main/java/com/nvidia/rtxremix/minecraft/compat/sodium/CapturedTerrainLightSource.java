/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

/** Prevents a provider from adding a second light for an emitter already captured with terrain. */
public interface CapturedTerrainLightSource {
    boolean rtxremix$hasCapturedTerrainLight();
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import com.nvidia.rtxremix.minecraft.terrain.capture.BlockDataQueue;
import com.nvidia.rtxremix.minecraft.terrain.capture.SectionBlockData;

/** Owns a CPU capture until Sodium accepts or destroys its build result. */
public interface SodiumCapturedOutput {
    void rtxremix$setCapture(SectionBlockData data, BlockDataQueue.CaptureToken token);
    boolean rtxremix$publishCapture();
}

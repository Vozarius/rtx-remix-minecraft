/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.minecraft.compat.framegen.FrameGenBridge;

public final class FrameGeneration {
    private static final boolean PRESENT = FrameGeneration.class.getClassLoader()
            .getResource("dev/screamix/framegen/client/FrameGenRuntime.class") != null;
    private FrameGeneration() {}
    public static void capture(int depthTexture) { if (PRESENT) FrameGenBridge.capture(depthTexture); }
    public static void close() { if (PRESENT) FrameGenBridge.close(); }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

/** Scopes raster distance checks which happen before the dispatcher opens a Remix model capture. */
public final class BlockEntityCaptureScope {
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private BlockEntityCaptureScope() {}
    public static void enter() {
        DEPTH.get()[0]++;
    }
    public static void exit() {
        DEPTH.get()[0]--;
    }
    public static boolean active() {
        return DEPTH.get()[0] > 0;
    }
}

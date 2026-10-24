/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

/** Class-presence guard keeps all renderer paths usable without the optional content addon. */
public final class ContentSupport {
    private static final boolean PRESENT = ContentSupport.class.getClassLoader().getResource(
                                                   "com/nvidia/rtxremix/content/RtxRemixContentMod.class")
            != null;
    private ContentSupport() {}
    public static boolean present() {
        return PRESENT;
    }
}

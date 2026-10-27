/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import com.nvidia.rtxremix.minecraft.light.LightEntry;

/** Immutable emission of the sprites used by one active block model, including deliberately black masks. */
public record EmissionProfile(float strength, LightEntry.ColorOverride color) {
    /** A resource-only emitter has no Minecraft light level; its full mask uses the level-15 energy. */
    public float effectiveLevel(int vanillaLevel) {
        return (vanillaLevel > 0 ? vanillaLevel : 15) * strength;
    }
}

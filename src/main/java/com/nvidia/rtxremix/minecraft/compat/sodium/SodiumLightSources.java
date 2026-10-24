/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import toni.sodiumdynamiclights.DynamicLightSource;

public interface SodiumLightSources {
    DynamicLightSource[] rtxremix$copyLightSources(DynamicLightSource[] reusable);
}

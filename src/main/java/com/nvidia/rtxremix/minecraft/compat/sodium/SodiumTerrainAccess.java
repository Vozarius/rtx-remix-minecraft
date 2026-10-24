/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import it.unimi.dsi.fastutil.longs.LongSet;

public interface SodiumTerrainAccess {
    LongSet rtxremix$builtSections();
    LongSet rtxremix$recaptureSections(LongSet sections);
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SodiumSectionCaptureTest {
    @Test void emptyBuildClearsGeometryLightsAndOcclusionTogether() {
        var data = SodiumSectionCapture.emptySection(-13, 4, 25);
        try {
            assertEquals(-13, data.sectionPos().getX());
            assertEquals(4, data.sectionPos().getY());
            assertEquals(25, data.sectionPos().getZ());
            assertTrue(data.meshData().isEmpty());
            assertTrue(data.lights().isEmpty());
            assertEquals(-1L, data.topFaceMask()); assertEquals(-1L, data.bottomFaceMask());
            assertEquals(-1L, data.eastFaceMask()); assertEquals(-1L, data.westFaceMask());
            assertEquals(-1L, data.southFaceMask()); assertEquals(-1L, data.northFaceMask());
            assertArrayEquals(new long[64], data.opacityBitmap());
        } finally { data.meshData().release(); }
    }
}

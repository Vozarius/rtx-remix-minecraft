/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TiledTextureCoordinatesTest {
    @Test void preservesSubBlockDetailsInRemotePlots() {
        float a = TiledTextureCoordinates.tile(30000000, .125f, 4);
        float b = TiledTextureCoordinates.tile(30000000, .25f, 4);
        assertEquals(.03125f, a);
        assertEquals(.0625f, b);
        assertNotEquals(a,b);
    }
    @Test void adjacentSectionsShareTheSameRepeatPhase() {
        for (int origin : new int[]{-30000000, -32, 0, 30000000}) {
            for (float size : new float[]{.5f, 3.5f, 4}) {
                double a = TiledTextureCoordinates.tile(origin,16,size);
                double b = TiledTextureCoordinates.tile(origin+16,0,size);
                double difference = a-b;
                assertEquals(Math.rint(difference), difference, 1e-6);
            }
        }
    }
}

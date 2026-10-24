/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TextureAverageTest {
    @Test
    void transparentRgbDoesNotTintTheLiquid() {
        TextureAverage a = TextureAverage.of(new byte[] {(byte) 255, 0, 0, 0, 0, 0, (byte) 255, (byte) 255});
        assertEquals(0, a.red());
        assertEquals(0, a.green());
        assertEquals(1, a.blue());
        assertEquals(.5f, a.alpha());
    }
    @Test
    void translucentFramesContributeByCoverage() {
        TextureAverage a = TextureAverage.of(new byte[] {(byte) 255, 0, 0, 64, 0, (byte) 255, 0, (byte) 192});
        assertEquals(.25f, a.red(), 1e-6);
        assertEquals(.75f, a.green(), 1e-6);
        assertEquals(128f / 255, a.alpha(), 1e-6);
    }
    @Test
    void invisibleTextureHasNoColour() {
        assertEquals(new TextureAverage(0, 0, 0, 0), TextureAverage.of(new byte[] {1, 2, 3, 0}));
    }
    @Test
    void emissionMaskUsesRgbAndAlphaRatherThanSpecularConventions() {
        assertEquals(1, EmissionMask.strength(0xffffffff));
        assertEquals(0, EmissionMask.strength(0xff000000));
        assertEquals(128f / 255, EmissionMask.strength(0x80ffffff), 1e-6);
    }
}

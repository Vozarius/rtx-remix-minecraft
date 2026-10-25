/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProceduralSurfaceMapsTest {
    @Test
    void flatSurfaceHasNoInventedReliefOrTilt() {
        byte[] pixels = new byte[3 * 3 * 4];
        java.util.Arrays.fill(pixels, (byte) 255);
        var result = ProceduralSurfaceMaps.generate(pixels, 3, 3, 4, 1);
        for (int i = 0; i < 9; i++) {
            assertEquals(128, result.normal()[i * 4] & 255);
            assertEquals(128, result.normal()[i * 4 + 1] & 255);
            assertEquals(255, result.normal()[i * 4 + 2] & 255);
            assertEquals(255, result.height()[i] & 255);
        }
    }
    @Test
    void transparentBorderCannotCreateARaisedSilhouette() {
        byte[] pixels = new byte[3 * 3 * 4];
        int center = 4 * 4;
        pixels[center] = (byte) 255;
        pixels[center + 3] = (byte) 255;
        var maps = ProceduralSurfaceMaps.generate(pixels, 3, 3, 4, 1);
        assertEquals(128, maps.normal()[center] & 255);
        assertEquals(128, maps.normal()[center + 1] & 255);
        assertEquals(255, maps.normal()[center + 2] & 255);
    }
    @Test
    void strengthAndDepthAreIndependentAndZeroActuallyDisablesThem() {
        byte[] pixels = new byte[3 * 3 * 4];
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++) {
                int i = (y * 3 + x) * 4;
                pixels[i] = pixels[i + 1] = pixels[i + 2] = (byte) (x * 100);
                pixels[i + 3] = (byte) 255;
            }
        var weak = ProceduralSurfaceMaps.generate(pixels, 3, 3, .25f, .1f);
        var strong = ProceduralSurfaceMaps.generate(pixels, 3, 3, 2, .1f);
        assertTrue((strong.normal()[16] & 255) < (weak.normal()[16] & 255));
        assertArrayEquals(weak.height(), strong.height());
        var disabled = ProceduralSurfaceMaps.generate(pixels, 3, 3, 0, 0);
        for (int i = 0; i < 9; i++) {
            assertEquals(128, disabled.normal()[i * 4] & 255);
            assertEquals(255, disabled.height()[i] & 255);
        }
    }
    @Test
    void coloredGlassRetainsItsDiffuseAlphaAndStrengthensSelectiveAbsorption() {
        byte[] source = {(byte) 255, 32, 32, 64}, transmission = new byte[4];
        GlassOptics.transmission(source, 0, transmission, 0);
        assertEquals(255, transmission[0] & 255);
        assertTrue((transmission[1] & 255) < 32);
        assertEquals(64, source[3] & 255, "The diffuse paint retains its original alpha");
    }
    @Test
    void clearGreyFrameDoesNotBecomeSmokeTintedGlass() {
        byte[] source = {80, 80, 80, 0}, result = new byte[4];
        GlassOptics.transmission(source, 0, result, 0);
        assertArrayEquals(new byte[] {-1, -1, -1, -1}, result);
    }
    @Test
    void ordinaryAlphaMaterialsUseTheOpaqueBlendExtension() throws Exception {
        var manager = new BlockTextureManager(null);
        var texture = new com.nvidia.rtxremix.minecraft.asset.RemixTexture(1, 1,
                com.nvidia.rtxremix.TextureFormat.RGBA8_UNORM, com.nvidia.rtxremix.TexturePurpose.COLOR,
                java.nio.ByteBuffer.allocateDirect(4));
        var field = BlockTextureManager.class.getDeclaredField("atlasAlbedo");
        field.setAccessible(true);
        field.set(manager, texture);
        var method = BlockTextureManager.class.getDeclaredMethod("buildAtlasTransMaterialInfo",
                com.nvidia.rtxremix.minecraft.asset.RemixTexture.class,
                com.nvidia.rtxremix.minecraft.asset.RemixTexture.class, float.class);
        method.setAccessible(true);
        var info = (com.nvidia.rtxremix.MaterialInfo) method.invoke(manager, null, null, 1f);
        assertNull(info.translucentExt);
        assertNotNull(info.opaqueExt);
        assertEquals(
                com.nvidia.rtxremix.MaterialInfoOpaqueEXT.BLEND_TYPE_ALPHA, info.opaqueExt.blendType_value);
        assertEquals(1, info.opaqueExt.useDrawCallAlphaState);
    }
}

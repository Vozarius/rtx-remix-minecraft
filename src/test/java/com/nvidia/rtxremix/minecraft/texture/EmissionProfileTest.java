/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import com.nvidia.rtxremix.minecraft.light.LightEntry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmissionProfileTest {
    private static float strength(byte[] albedo, byte[] emission) {
        return new EmissionImage(albedo, emission, albedo.length / 4, 1).sample(0, 0, 1, 1).strength();
    }

    @Test
    void anOffModelUsingTheDarkPartOfAnOnTextureDoesNotCreateLight() {
        byte[] albedo = {-1, -1, -1, -1, -1, -1, -1, -1};
        byte[] emission = {0, 0, 0, 0, -1, -1, -1, -1};
        var image = new EmissionImage(albedo, emission, 2, 1);
        assertEquals(0, image.sample(0, 0, .5f, 1).strength());
        assertEquals(1, image.sample(.5f, 0, 1, 1).strength());
        assertSame(image.sample(0, 0, .5f, 1), image.sample(0, 0, .5f, 1),
                "UV regions shared by thousands of block states are measured once at reload");
    }

    @Test
    void suppliedLightbulbMaskProducesWarmWorldLight() throws Exception {
        try (var jar = new java.util.zip.ZipFile("reference/bits_n_bobs-2.3.5.jar");
                var source = jar.getInputStream(
                        jar.getEntry("assets/bits_n_bobs/textures/block/lightbulb_on.png"));
                var mask = getClass().getResourceAsStream(
                        "/assets/bits_n_bobs/textures/block/lightbulb_on_e.png")) {
            assertNotNull(mask);
            var image = javax.imageio.ImageIO.read(source);
            var maskImage = javax.imageio.ImageIO.read(mask);
            int w = image.getWidth(), h = image.getHeight();
            byte[] albedo = new byte[w * h * 4], emission = new byte[albedo.length];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int rgba = image.getRGB(x, y), m = maskImage.getRGB(x, y);
                    float gain =
                            EmissionMask.strength((m & 0xff00ff00) | ((m >>> 16) & 255) | ((m & 255) << 16));
                    int i = (y * w + x) * 4;
                    for (int c = 0; c < 3; c++) {
                        int channel = (rgba >>> (16 - c * 8)) & 255;
                        albedo[i + c] = (byte) channel;
                        emission[i + c] = (byte) Math.round(channel * gain);
                    }
                    albedo[i + 3] = (byte) (rgba >>> 24);
                    emission[i + 3] = gain > 0 ? albedo[i + 3] : 0;
                }
            EmissionTint.apply(EmissionTint.LIGHTBULB_ON, emission, w, 0, 0, w, h);
            float strength = strength(albedo, emission);
            assertTrue(strength > 0.5f, "The supplied mask must retain useful lamp power: " + strength);
            float[] color = BlockTextureManager.computeLightColor(albedo, emission, w, h);
            assertTrue(color[0] > color[1] && color[1] > color[2]);
            assertTrue(
                    new EmissionProfile(strength, LightEntry.ColorOverride.of(color[0], color[1], color[2]))
                            .effectiveLevel(15)
                    > 7.5f);
        }
    }

    @Test
    void aTinyWhiteFilamentKeepsItsLightLevelDespiteUnusedMaskArea() {
        byte[] albedo = new byte[32 * 32 * 4], emission = new byte[albedo.length];
        for (int i = 0; i < albedo.length; i++) albedo[i] = -1;
        for (int i = 0; i < 4; i++) emission[i] = -1;
        float strength = strength(albedo, emission);
        assertEquals(1, strength);
        var profile = new EmissionProfile(strength, LightEntry.ColorOverride.of(1, .76f, .42f));
        assertEquals(15, profile.effectiveLevel(15));
        assertEquals(15, profile.effectiveLevel(0), "resource-only emitters must also light the world");
        assertEquals(7, profile.effectiveLevel(7), "redstone dimming remains multiplicative");
        assertEquals(0, strength(albedo, new byte[albedo.length]));
    }

    @Test
    void greyAndInvisibleMaskPixelsDoNotBecomeFullPowerEmitters() {
        byte[] albedo = {-1, -1, -1, -1, -1, -1, -1, 0};
        byte[] emission = {(byte) 128, (byte) 128, (byte) 128, -1, -1, -1, -1, -1};
        float strength = strength(albedo, emission);
        assertEquals(128f / 255, strength, 1e-6);
        assertEquals(15 * 128f / 255,
                new EmissionProfile(strength, LightEntry.ColorOverride.NONE).effectiveLevel(0), 1e-6);
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import com.nvidia.rtxremix.*;
import com.nvidia.rtxremix.minecraft.asset.RemixTexture;
import net.minecraft.util.Identifier;
import java.nio.ByteBuffer;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowMaterialTest {
    private static boolean transparent(Identifier id) {
        try (var stream = WindowMaterialTest.class.getResourceAsStream(
                     "/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png")) {
            assertNotNull(stream, id.toString());
            var image = ImageIO.read(stream);
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                    if ((image.getRGB(x, y) >>> 24) != 255)
                        return true;
            return false;
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void windowFramesDoNotTurnVanillaPlanksIntoGlass() {
        var planks = Identifier.ofVanilla("block/oak_planks");
        var glass = Identifier.ofVanilla("block/glass");
        var usage = new GlassSpriteUsage();
        usage.add(true, Set.of(planks, glass));
        usage.add(false, Set.of(planks));
        assertEquals(Set.of(glass), usage.resolve(WindowMaterialTest::transparent));
        assertEquals("solid", BlockSurfaceLayer.resolve("glass", false, false, false));
        // A glass sprite borrowed by a tank remains glass even though the tank is not a glass block.
        usage.add(false, Set.of(glass));
        assertEquals(Set.of(glass), usage.resolve(WindowMaterialTest::transparent));
    }
    @Test
    void alphaOnAnOtherwiseSolidModLayerIsKeptWithoutRefraction() {
        assertEquals("cutout", BlockSurfaceLayer.resolve("solid", false, true, false));
        assertEquals("translucent", BlockSurfaceLayer.resolve("solid", false, true, true));
        assertEquals("cutout", BlockSurfaceLayer.resolve("translucent", false, true, false));
        assertEquals("glass", BlockSurfaceLayer.resolve("cutout", true, true, false));
        assertEquals(1, BlockTextureManager.ATLAS_TRANSLUCENT_BLEND.alphaBlendEnabled);
        assertEquals(0, BlockTextureManager.ATLAS_TRANSLUCENT_BLEND.alphaTestReferenceValue,
                "Every positive alpha value must retain its fractional coverage");
        assertEquals(InstanceInfoBlendEXT.VK_BLEND_FACTOR_SRC_ALPHA,
                BlockTextureManager.ATLAS_TRANSLUCENT_BLEND.srcColorBlendFactor);
        assertEquals(InstanceInfoBlendEXT.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                BlockTextureManager.ATLAS_TRANSLUCENT_BLEND.dstColorBlendFactor);
    }
    @Test
    void glassBaseDoesNotUsePaintAlphaAndOpaquePaintDoesNotTintTransmission() throws Exception {
        var manager = new BlockTextureManager(null);
        var texture = new RemixTexture(
                1, 1, TextureFormat.RGBA8_UNORM, TexturePurpose.COLOR, ByteBuffer.allocateDirect(4));
        var transmissionField = BlockTextureManager.class.getDeclaredField("glassTransmissionAtlas");
        transmissionField.setAccessible(true);
        transmissionField.set(manager, texture);
        var info = manager.buildAtlasGlassMaterialInfo(null);
        assertEquals(0, info.albedoTexture);
        assertEquals(0, info.emissiveTexture);
        assertNull(info.opaqueExt);
        assertEquals(0, info.translucentExt.useDiffuseLayer);
        byte[] result = new byte[4];
        GlassOptics.transmission(new byte[] {(byte) 180, 70, 20, (byte) 255}, 0, result, 0);
        assertArrayEquals(new byte[] {-1, -1, -1, -1}, result);
        GlassOptics.transmission(new byte[] {(byte) 255, 0, 0, 0}, 0, result, 0);
        assertArrayEquals(new byte[] {-1, -1, -1, -1}, result);
    }
    @Test
    void lightbulbTintWarmsOnlyItsEmissionAndKeepsMaskStrength() {
        byte[] pixels = {-1, -1, -1, -1, 0, 0, 0, 0, 64, 64, 64, (byte) 128};
        var before = pixels.clone();
        EmissionTint.apply(Identifier.of("bits_n_bobs", "block/headlamp_on"), pixels, 3, 0, 0, 3, 1);
        assertArrayEquals(before, pixels);
        EmissionTint.apply(EmissionTint.LIGHTBULB_ON, pixels, 3, 0, 0, 3, 1);
        assertEquals(255, pixels[0] & 255);
        assertTrue((pixels[1] & 255) > (pixels[2] & 255));
        for (int i = 0; i < pixels.length; i += 4) {
            assertEquals(before[i], pixels[i], "red/peak emission and mask strength remain unchanged");
            assertEquals(before[i + 3], pixels[i + 3], "mask alpha must stay unchanged");
        }
        assertEquals(0, pixels[4]);
        assertTrue(EmissionTint.lightbulbColor()[1] > EmissionTint.lightbulbColor()[2]);
    }
}

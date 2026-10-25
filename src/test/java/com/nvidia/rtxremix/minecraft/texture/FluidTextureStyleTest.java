/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FluidTextureStyleTest {
    @Test
    void exceptionsFollowTheFluidOwnerNotItsSharedWaterSprite() {
        for (String id : new String[] {"aeronautics:levitite_blend", "create_enchantment_industry:experience",
                     "minecraft:lava", "minecraft:flowing_lava"})
            assertTrue(FluidTextureStyle.lava(id), id);
        for (String id : new String[] {
                     "create:honey", "tfmg:gasoline", "create_dragons_plus:dragon_breath", "minecraft:water"})
            assertFalse(FluidTextureStyle.lava(id), id);
    }
    @Test
    void recolouringRetainsAnimationAndOriginalCoverage() {
        byte[] base = {64, 64, 64, -1, (byte) 192, (byte) 192, (byte) 192, -1};
        byte[] result = FluidTextureStyle.recolor(base, new TextureAverage(.4f, .2f, .1f, .3f));
        assertEquals(Math.round(.3f * 255), result[3] & 255);
        assertEquals(result[3], result[7]);
        assertTrue((result[4] & 255) > (result[0] & 255));
        assertEquals(.4f, TextureAverage.of(result).red(), .01f);
        assertEquals(.2f, TextureAverage.of(result).green(), .01f);
    }
    @Test
    void saturatedColourDoesNotLoseItsMeanToHighlightClipping() {
        byte[] base = {0, 0, 0, -1, -1, -1, -1, -1};
        var average =
                TextureAverage.of(FluidTextureStyle.recolor(base, new TextureAverage(.95f, .4f, .1f, .65f)));
        assertEquals(.95f, average.red(), .005f);
        assertEquals(.4f, average.green(), .005f);
        assertEquals(.1f, average.blue(), .005f);
        assertEquals(.65f, average.alpha(), .005f);
    }
}

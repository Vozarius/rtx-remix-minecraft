/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.light;

import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;

/** Unauthored luminous states use outward area emitters, outside the block's own shadow geometry. */
public final class AutomaticBlockLights {
    private static final Identifier RULE = Identifier.of("rtx-remix-renderer", "automatic_block_emission");
    private static final Direction[] SIDES = Direction.values();
    private AutomaticBlockLights() {}
    public static boolean supports(BlockState state) {
        return supports(state, state.getLuminance());
    }

    public static boolean supports(BlockState state, int luminance) {
        return luminance > 0 && !state.getBlock().getClass().getName().endsWith(".CopperBeaconBlock")
                && !state.getBlock().getClass().getName().equals(
                        "com.kipti.bnb.content.trinkets.light.headlamp.HeadlampBlock");
    }
    public static List<LightEntry> resolve(BlockState state, BlockPos pos) {
        return resolve(state, pos, state.getLuminance());
    }

    public static List<LightEntry> resolve(BlockState state, BlockPos pos, int luminance) {
        if (!supports(state, luminance))
            return List.of();
        var textures = com.nvidia.rtxremix.minecraft.texture.BlockTextureManager.get();
        float maskScale = textures == null ? 1 : textures.emissionCoverage(state.getBlock());
        if (maskScale <= 0)
            return List.of();
        List<LightEntry> result = new ArrayList<>(6);
        for (Direction side : SIDES) {
            int nx = side.getOffsetX(), ny = side.getOffsetY(), nz = side.getOffsetZ();
            float xx = ny == 0 ? 0 : 1, xy = ny == 0 ? 1 : 0;
            float yx = -nz, yz = ny != 0 ? -ny : nx;
            result.add(LightEntry.rect(new BlockLightKey(pos.toImmutable(), RULE, side.getId()), luminance,
                    state.getBlock(), pos.getX() + 0.5 + nx * 0.505, pos.getY() + 0.5 + ny * 0.505,
                    pos.getZ() + 0.5 + nz * 0.505, xx, xy, 0, 0.9f, yx, 0, yz, 0.9f, nx, ny, nz, null,
                    luminance * BlockLightRuleManager.RADIANCE_PER_LIGHT_LEVEL * maskScale / 6f,
                    LightEntry.defaultVolumetricRadianceScale()));
        }
        return result;
    }
}

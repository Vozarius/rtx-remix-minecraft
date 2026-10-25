/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.bits;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeadlampPoseTest {
    @Test
    void emptyPlacementsAndComputerCraftAddressingAreIndependent() {
        assertFalse(HeadlampPose.lit(15, 4));
        long allPlacements = 0;
        for (int slot = 0; slot < 9; slot++) allPlacements |= 1L << (4 + slot * 6);
        for (int slot = 0; slot < 9; slot++) assertTrue(HeadlampPose.lit(allPlacements | 15, slot));
        assertFalse(HeadlampPose.lit(allPlacements, 4));
        assertTrue(HeadlampPose.lit(allPlacements | 1, 4));
        assertFalse(HeadlampPose.lit(allPlacements | 1, 0));
        assertTrue(HeadlampPose.lit(allPlacements | 8, 0));
    }
    @Test
    void beamFacesEveryMountAndFollowsItsPlacement() {
        for (Direction facing : Direction.values()) {
            var first = HeadlampPose.definition();
            var last = HeadlampPose.definition();
            HeadlampPose.configure(first, 15 | (1L << 4), 0, facing);
            HeadlampPose.configure(last, 15 | (1L << (4 + 8 * 6)), 8, facing);
            var disk = first.diskExt;
            assertEquals(facing.getOffsetX(), disk.direction.x, .0001);
            assertEquals(facing.getOffsetY(), disk.direction.y, .0001);
            assertEquals(facing.getOffsetZ(), disk.direction.z, .0001);
            var other = last.diskExt.position;
            float dx = disk.position.x - other.x, dy = disk.position.y - other.y,
                  dz = disk.position.z - other.z;
            assertEquals(.5f, dx * dx + dy * dy + dz * dz, .0001);
            assertTrue(first.radiance.x > 0);
            assertTrue(disk.shaping_hasvalue);
        }
    }
}

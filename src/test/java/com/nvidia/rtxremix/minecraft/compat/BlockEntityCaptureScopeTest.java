/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlockEntityCaptureScopeTest {
    @Test
    void nestedBlockEntitiesRestoreTheOuterDistancePolicyAfterFailure() {
        assertFalse(BlockEntityCaptureScope.active());
        BlockEntityCaptureScope.enter();
        try {
            assertThrows(IllegalStateException.class, () -> {
                BlockEntityCaptureScope.enter();
                try {
                    throw new IllegalStateException();
                } finally {
                    BlockEntityCaptureScope.exit();
                }
            });
            assertTrue(BlockEntityCaptureScope.active());
        } finally {
            BlockEntityCaptureScope.exit();
        }
        assertFalse(BlockEntityCaptureScope.active());
    }
}

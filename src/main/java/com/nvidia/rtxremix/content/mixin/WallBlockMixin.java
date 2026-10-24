/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.content.mixin;

import com.nvidia.rtxremix.content.CopperBeaconBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.WallBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
abstract class WallBlockMixin {

    @Inject(method = "shouldConnectTo", at = @At("HEAD"), cancellable = true)
    private void rtxremix$connectToCopperBeacon(
            BlockState state,
            boolean faceFullSquare,
            Direction side,
            CallbackInfoReturnable<Boolean> callback) {
        if (state.getBlock() instanceof CopperBeaconBlock) {
            callback.setReturnValue(
                    state.get(CopperBeaconBlock.MOUNT) == side.getOpposite());
        }
    }

    @Inject(method = "shouldHavePost", at = @At("HEAD"), cancellable = true)
    private void rtxremix$connectPostToCopperBeacon(
            BlockState state,
            BlockState aboveState,
            VoxelShape aboveShape,
            CallbackInfoReturnable<Boolean> callback) {
        if (aboveState.getBlock() instanceof CopperBeaconBlock) {
            callback.setReturnValue(
                    aboveState.get(CopperBeaconBlock.MOUNT) == Direction.UP);
        }
    }
}

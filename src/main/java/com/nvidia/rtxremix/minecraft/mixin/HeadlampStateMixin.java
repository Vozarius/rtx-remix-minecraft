/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;
import com.nvidia.rtxremix.minecraft.compat.bits.HeadlampAccess;
import org.spongepowered.asm.mixin.*;
@Pseudo
@Mixin(targets = "com.kipti.bnb.content.trinkets.light.headlamp.HeadlampBlockEntity", remap = false)
public abstract class HeadlampStateMixin implements HeadlampAccess {
    @Shadow public abstract long getRenderStateAsLong();
    @Unique
    public long rtxremix$headlampState() {
        return getRenderStateAsLong();
    }
}

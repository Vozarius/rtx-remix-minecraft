/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.util.math.ChunkPos;

import com.nvidia.rtxremix.Transform;
import com.nvidia.rtxremix.minecraft.compat.SpatialFrame;
import com.nvidia.rtxremix.minecraft.compat.FrameTransform;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.joml.Matrix3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(value = ClientSubLevel.class, remap = false)
public abstract class SableSpatialFrameMixin implements SpatialFrame {
    @Unique private final Matrix3d rtxremix$rotation = new Matrix3d();

    @Override
    public boolean rtxremix$isAlive() {
        return !((ClientSubLevel)(Object)this).isRemoved();
    }

    @Override
    public boolean rtxremix$isSectionEmpty(int chunkX, int sectionY, int chunkZ) {
        var plot = ((ClientSubLevel)(Object)this).getPlot();
        var chunk = plot.getChunk(plot.toLocal(new ChunkPos(chunkX, chunkZ)));
        return chunk == null || chunk.isSectionEmpty(sectionY);
    }

    @Override
    public void rtxremix$transform(double x, double y, double z, Transform out) {
        var pose = ((ClientSubLevel)(Object)this).renderPose();
        pose.orientation().get(rtxremix$rotation);
        FrameTransform.set(rtxremix$rotation, pose.scale(), pose.rotationPoint(), pose.position(), x, y, z, out);
    }
}

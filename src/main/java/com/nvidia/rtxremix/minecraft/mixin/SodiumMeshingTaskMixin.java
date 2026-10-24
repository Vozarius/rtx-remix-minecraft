/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumSectionCapture;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public abstract class SodiumMeshingTaskMixin extends ChunkBuilderTask<ChunkBuildOutput> {
    protected SodiumMeshingTaskMixin(RenderSection render, int time, org.joml.Vector3dc camera) {
        super(render, time, camera);
    }

    @WrapMethod(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;")
    private ChunkBuildOutput rtxremix$capture(ChunkBuildContext context, CancellationToken cancellation, Operation<ChunkBuildOutput> original) {
        var capture = SodiumSectionCapture.current();
        capture.begin(render);
        try {
            var output = original.call(context, cancellation);
            if (output != null) capture.finish(output);
            return output;
        } finally {
            capture.close();
        }
    }

    @WrapOperation(method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
        at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/world/LevelSlice;getBlockState(III)Lnet/minecraft/block/BlockState;", remap = true))
    private BlockState rtxremix$block(LevelSlice slice, int x, int y, int z, Operation<BlockState> original) {
        BlockState state = original.call(slice, x, y, z);
        SodiumSectionCapture.current().block(slice, state, x, y, z);
        return state;
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import java.util.Map;
import java.util.ArrayDeque;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumTerrainAccess;
import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumSectionCapture;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumCapturedOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class SodiumSectionManagerMixin implements SodiumTerrainAccess {
    @Shadow @Final private Long2ReferenceMap<RenderSection> sectionByPosition;
    @Shadow private Map<TaskQueueType, ArrayDeque<RenderSection>> taskLists;
    @Shadow public abstract void scheduleRebuild(int x, int y, int z, boolean important);
    @Unique private final LongSet rtxremix$recaptures = new LongOpenHashSet();

    @Override public LongSet rtxremix$builtSections() {
        var built = new LongOpenHashSet();
        for (var section : sectionByPosition.values()) if (section.isBuilt() && !section.isDisposed()) built.add(section.getPosition().asLong());
        return built;
    }

    @Override public LongSet rtxremix$recaptureSections(LongSet sections) {
        var accepted = new LongOpenHashSet();
        for (var iterator = sections.iterator(); iterator.hasNext();) {
            long key = iterator.nextLong();
            var section = sectionByPosition.get(key);
            if (section == null || section.isDisposed()) continue;
            scheduleRebuild(section.getChunkX(), section.getChunkY(), section.getChunkZ(), false);
            accepted.add(key);
            rtxremix$recaptures.add(key);
        }
        return accepted;
    }

    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void rtxremix$queueUnseenCaptures(boolean immediate, CallbackInfo ci) {
        if (rtxremix$recaptures.isEmpty()) return;
        var tasks = taskLists.get(TaskQueueType.ALWAYS_DEFER);
        for (var iterator = rtxremix$recaptures.iterator(); iterator.hasNext();) {
            var section = sectionByPosition.get(iterator.nextLong());
            if (section == null || section.isDisposed() || (section.isBuilt() && section.getPendingUpdate() == 0 && section.getRunningJob() == null)) {
                iterator.remove();
            } else if (section.getPendingUpdate() != 0 && !tasks.contains(section)) tasks.add(section);
        }
    }

    @Inject(method = "filterChunkBuildResults", at = @At("RETURN"))
    private static void rtxremix$accepted(ArrayList<BuilderTaskOutput> outputs, CallbackInfoReturnable<List<BuilderTaskOutput>> cir) {
        for (var output : cir.getReturnValue()) {
            if (output instanceof SodiumCapturedOutput captured && !captured.rtxremix$publishCapture()
                    && output instanceof ChunkBuildOutput build) {
                SodiumSectionCapture.publishEmpty(build);
            }
        }
    }
}

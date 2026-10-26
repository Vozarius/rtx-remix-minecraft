/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.nvidia.rtxremix.minecraft.compat.sodium.SodiumCaptureScheduling;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockDataQueue;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
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
    @Shadow @Final private ClientWorld level;
    @Shadow private Map<TaskQueueType, ArrayDeque<RenderSection>> taskLists;
    @Shadow public abstract void scheduleRebuild(int x, int y, int z, boolean important);
    @Unique private final ReferenceOpenHashSet<RenderSection> rtxremix$queued = new ReferenceOpenHashSet<>();
    @Unique private final LongLinkedOpenHashSet rtxremix$recaptures = new LongLinkedOpenHashSet();
    @Unique private final long[] rtxremix$retry = new long[128];

    @Override
    public LongSet rtxremix$builtSections() {
        return SodiumCaptureScheduling.builtSections(sectionByPosition.values(), this::rtxremix$hasContents);
    }

    @Override
    public LongSet rtxremix$recaptureSections(LongSet sections) {
        var accepted = new LongOpenHashSet();
        long[] ordered = sections.toLongArray();
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        LongArrays.quickSort(ordered,
                (a, b)
                        -> Double.compare(
                                rtxremix$distanceSquared(a, camera), rtxremix$distanceSquared(b, camera)));
        for (long key : ordered) {
            var section = sectionByPosition.get(key);
            if (section == null || section.isDisposed() || !rtxremix$hasContents(section))
                continue;
            scheduleRebuild(section.getChunkX(), section.getChunkY(), section.getChunkZ(), false);
            accepted.add(key);
            rtxremix$recaptures.add(key);
        }
        // Existing initial-build requests may predate the sorted full recapture.
        // Move these requests ahead of that ring without dropping its other work.
        for (int i = ordered.length - 1; i >= 0; i--)
            if (accepted.contains(ordered[i]))
                rtxremix$recaptures.addAndMoveToFirst(ordered[i]);
        return accepted;
    }

    @Unique
    private boolean rtxremix$hasContents(RenderSection section) {
        // Sodium marks air sections built without running a mesh task. Full recapture has
        // already cleared Remix; only ordinary edits must publish an empty replacement.
        var chunk = level.getChunkManager().getWorldChunk(section.getChunkX(), section.getChunkZ(), false);
        return chunk != null && !chunk.isSectionEmpty(section.getChunkY());
    }

    @Unique
    private static double rtxremix$distanceSquared(long key, Vec3d camera) {
        double x = ChunkSectionPos.unpackX(key) * 16.0 + 8 - camera.x;
        double y = ChunkSectionPos.unpackY(key) * 16.0 + 8 - camera.y;
        double z = ChunkSectionPos.unpackZ(key) * 16.0 + 8 - camera.z;
        return x * x + y * y + z * z;
    }

    // Initial builds do not call scheduleRebuild; Sodium normally discovers them through its camera walk.
    @Inject(method = "onSectionAdded", at = @At("RETURN"))
    private void rtxremix$trackInitial(int x, int y, int z, CallbackInfo ci) {
        long key = ChunkSectionPos.asLong(x, y, z);
        var section = sectionByPosition.get(key);
        if (section != null && section.getPendingUpdate() != 0)
            rtxremix$recaptures.add(key);
    }
    @Inject(method = "onSectionRemoved", at = @At("HEAD"))
    private void rtxremix$forgetRemoved(int x, int y, int z, CallbackInfo ci) {
        rtxremix$recaptures.remove(ChunkSectionPos.asLong(x, y, z));
    }
    @Inject(method = "scheduleRebuild", at = @At("RETURN"))
    private void rtxremix$trackWorldChange(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (!RtxRemixMod.isRtxEnabled())
            return;
        long key = ChunkSectionPos.asLong(x, y, z);
        var section = sectionByPosition.get(key);
        if (section != null && !section.isDisposed() && section.getPendingUpdate() != 0)
            rtxremix$recaptures.add(key);
    }
    @Inject(method = "updateChunks", at = @At("HEAD"))
    private void rtxremix$queueUnseenCaptures(boolean immediate, CallbackInfo ci) {
        if (immediate || !RtxRemixMod.isRtxEnabled() || rtxremix$recaptures.isEmpty())
            return;
        rtxremix$queued.clear();
        for (var queue : taskLists.values()) rtxremix$queued.addAll(queue);
        int allowance = SodiumCaptureScheduling.extraBuildAllowance(
                BlockDataQueue.pendingCount(), BlockDataQueue.pendingBytes(), rtxremix$queued.size());
        if (allowance == 0) {
            rtxremix$queued.clear();
            return;
        }
        int visited = 0, retry = 0;
        var iterator = rtxremix$recaptures.iterator();
        while (iterator.hasNext() && visited++ < rtxremix$retry.length && allowance > 0) {
            long key = iterator.nextLong();
            iterator.remove();
            var section = sectionByPosition.get(key);
            if (section == null || section.isDisposed())
                continue;
            int pending = section.getPendingUpdate();
            if (section.isBuilt() && !SodiumCaptureScheduling.needsMesh(pending)
                    && section.getRunningJob() == null)
                continue;
            rtxremix$retry[retry++] = key;
            if (SodiumCaptureScheduling.enqueue(section, taskLists, rtxremix$queued))
                allowance--;
        }
        for (int i = 0; i < retry; i++) rtxremix$recaptures.add(rtxremix$retry[i]);
        rtxremix$queued.clear();
    }

    @Inject(method = "filterChunkBuildResults", at = @At("RETURN"))
    private static void rtxremix$accepted(
            ArrayList<BuilderTaskOutput> outputs, CallbackInfoReturnable<List<BuilderTaskOutput>> cir) {
        for (var output : cir.getReturnValue()) {
            if (output instanceof SodiumCapturedOutput captured)
                captured.rtxremix$publishCapture();
        }
    }
}

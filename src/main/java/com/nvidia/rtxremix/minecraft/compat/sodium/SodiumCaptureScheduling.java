/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.*;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;

/** Adds off-camera work without bypassing Sodium's running-job exclusion or its queue limits. */
public final class SodiumCaptureScheduling {
    private SodiumCaptureScheduling() {}
    /** Built air sections have no Remix replacement after a full terrain clear. */
    public static it.unimi.dsi.fastutil.longs.LongSet builtSections(
            Iterable<RenderSection> sections, java.util.function.Predicate<RenderSection> hasContents) {
        var built = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        for (var section : sections) {
            if (section.isBuilt() && !section.isDisposed() && hasContents.test(section))
                built.add(section.getPosition().asLong());
        }
        return built;
    }

    /** Leaves the foreground renderer and the bounded capture queue room to drain. */
    public static int extraBuildAllowance(int pendingCaptures, long pendingBytes, int foregroundBuilds) {
        if (pendingCaptures >= 64 || pendingBytes >= 16L * 1024 * 1024)
            return 0;
        return Math.max(0, Math.min(8 - foregroundBuilds, 64 - pendingCaptures));
    }

    public static boolean needsMesh(int pending) {
        return ChunkUpdateTypes.isInitialBuild(pending) || ChunkUpdateTypes.isRebuild(pending);
    }
    public static boolean enqueue(RenderSection section, Map<TaskQueueType, ArrayDeque<RenderSection>> queues,
            Set<RenderSection> queued) {
        int pending = section.getPendingUpdate();
        if (section.isDisposed() || section.getRunningJob() != null || !needsMesh(pending))
            return false;
        var type = ChunkUpdateTypes.isInitialBuild(pending) ? TaskQueueType.INITIAL_BUILD
                                                            : TaskQueueType.ALWAYS_DEFER;
        var queue = queues.get(type);
        if (queue.size() >= type.queueSizeLimit() || !queued.add(section))
            return false;
        queue.addLast(section);
        return true;
    }
}

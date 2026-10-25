/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJob;
import java.util.*;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SodiumCaptureSchedulingTest {
    private static Map<TaskQueueType, ArrayDeque<RenderSection>> queues() {
        var queues = new EnumMap<TaskQueueType, ArrayDeque<RenderSection>>(TaskQueueType.class);
        for (var type : TaskQueueType.values()) queues.put(type, new ArrayDeque<>());
        return queues;
    }
    @Test
    void worldEditsWaitForTheRunningSodiumJobWithoutCancellingOrReplacingIt() {
        var section = new RenderSection(null, 1, 2, 3);
        section.setPendingUpdate(ChunkUpdateTypes.REBUILD, 0);
        var job = (ChunkJob) Proxy.newProxyInstance(
                ChunkJob.class.getClassLoader(), new Class<?>[] {ChunkJob.class}, (proxy, method, args) -> {
                    throw new AssertionError("Capture must not call into the running job");
                });
        section.setRunningJob(job);
        var queues = queues();
        Set<RenderSection> seen = new HashSet<>();
        assertFalse(SodiumCaptureScheduling.enqueue(section, queues, seen));
        assertSame(job, section.getRunningJob());
        assertEquals(ChunkUpdateTypes.REBUILD, section.getPendingUpdate());
        assertTrue(queues.values().stream().allMatch(ArrayDeque::isEmpty));
        section.setRunningJob(null);
        assertTrue(SodiumCaptureScheduling.enqueue(section, queues, seen));
        assertFalse(SodiumCaptureScheduling.enqueue(section, queues, seen));
        assertSame(section, queues.get(TaskQueueType.ALWAYS_DEFER).getFirst());
    }
    @Test
    void offscreenInitialBuildsUseTheInitialQueueAndNeverScheduleSorting() {
        var section = new RenderSection(null, -1, 8, 3);
        var queues = queues();
        Set<RenderSection> seen = new HashSet<>();
        section.setPendingUpdate(ChunkUpdateTypes.INITIAL_BUILD, 0);
        assertTrue(SodiumCaptureScheduling.enqueue(section, queues, seen));
        assertSame(section, queues.get(TaskQueueType.INITIAL_BUILD).getFirst());
        assertTrue(queues.get(TaskQueueType.ALWAYS_DEFER).isEmpty());
        section.clearPendingUpdate();
        section.setPendingUpdate(ChunkUpdateTypes.SORT, 0);
        seen.clear();
        assertFalse(SodiumCaptureScheduling.enqueue(section, queues, seen));
    }
    @Test
    void extraWorkStopsBeforeCaptureBackpressureAndYieldsToForegroundTasks() {
        assertEquals(8, SodiumCaptureScheduling.extraBuildAllowance(0, 0, 0));
        assertEquals(2, SodiumCaptureScheduling.extraBuildAllowance(20, 0, 6));
        assertEquals(0, SodiumCaptureScheduling.extraBuildAllowance(0, 0, 9));
        assertEquals(0, SodiumCaptureScheduling.extraBuildAllowance(64, 0, 0));
        assertEquals(0, SodiumCaptureScheduling.extraBuildAllowance(0, 16L * 1024 * 1024, 0));
        assertEquals(1, SodiumCaptureScheduling.extraBuildAllowance(63, 0, 0));
        assertEquals(8, SodiumCaptureScheduling.extraBuildAllowance(0, 15L * 1024 * 1024, 0));
    }
    @Test
    void fullRecaptureSkipsBuiltAirButKeepsNonemptySectionsWithNoVisibleQuads() {
        var sections = new ArrayList<RenderSection>();
        var empty = net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo.EMPTY;
        for (int i = 0; i < 10000; i++) {
            var section = new RenderSection(null, i, 0, 0);
            section.setInfo(empty);
            sections.add(section);
        }
        var unbuilt = new RenderSection(null, 10000, 0, 0);
        sections.add(unbuilt);
        var disposed = new RenderSection(null, 10001, 0, 0);
        disposed.setInfo(empty);
        disposed.delete();
        sections.add(disposed);
        // A solid interior can have the same zero-quad render info as air.
        var solid = sections.get(123);
        var result = SodiumCaptureScheduling.builtSections(
                sections, section -> section == solid || section == unbuilt || section == disposed);
        assertEquals(1, result.size());
        assertTrue(result.contains(solid.getPosition().asLong()));
    }
}

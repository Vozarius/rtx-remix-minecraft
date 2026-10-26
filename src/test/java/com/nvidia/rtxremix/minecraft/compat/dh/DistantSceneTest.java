/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.dh;

import com.nvidia.rtxremix.MaterialInfo;
import com.nvidia.rtxremix.RemixAPI;
import com.nvidia.rtxremix.minecraft.asset.*;
import net.minecraft.util.math.Vec3d;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistantSceneTest {
    @AfterEach
    void clear() {
        DistantScene.clear(null);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static DistantLodData data(int pages) {
        return new DistantLodData(4096, -64, 4096, 64,
                List.of(ByteBuffer.allocate(pages * DistantLodData.PAGE_BYTES)), List.of());
    }
    private static final class Instance extends RemixInstance {
        int draws, hides;
        Instance() {
            super(new RemixMesh(new RemixMeshSurface[0]));
        }
        @Override
        public void drawFast(RemixAPI api) {
            draws++;
        }
        @Override
        public void hideFromScene(RemixAPI api) {
            hides++;
        }
    }
    @Test
    void changingDhBufferKeepsPreviousTileVisibleUntilAllReplacementPagesAreReady() throws Exception {
        field(DistantScene.class, "opaque").set(null, new RemixMaterial(MaterialInfo.builder().build()));
        field(DistantScene.class, "transparent").set(null, new RemixMaterial(MaterialInfo.builder().build()));
        var old = data(0);
        DistantScene.beginFrame();
        DistantScene.select(old);
        DistantScene.update(null, Vec3d.ZERO, 2, null);
        var entries = (Map<?, ?>) field(DistantScene.class, "ENTRIES").get(null);
        var entry = entries.values().iterator().next();
        var instance = new Instance();
        ((List<RemixInstance>) field(entry.getClass(), "active").get(entry)).add(instance);
        old.closed = true;
        var replacement = data(3);
        DistantScene.beginFrame();
        DistantScene.select(replacement);
        DistantScene.update(null, Vec3d.ZERO, 2, null);
        assertEquals(1, entries.size(), "buffer versions must share a spatial tile owner");
        assertSame(entry, entries.values().iterator().next());
        assertSame(replacement, field(entry.getClass(), "data").get(entry));
        assertEquals(0, instance.hides, "the replacement still has an unbuilt page");
        assertTrue(instance.draws > 0);
        DistantScene.beginFrame();
        DistantScene.select(replacement);
        DistantScene.update(null, Vec3d.ZERO, 2, null);
        assertEquals(1, instance.hides, "only the completed replacement can remove the old version");
    }
    @Test
    void retirementContinuesAfterTheLastDhTileLeavesTheScene() throws Exception {
        var old = data(0);
        DistantScene.beginFrame();
        DistantScene.select(old);
        DistantScene.update(null, Vec3d.ZERO, 2, null);
        var entries = (Map<?, ?>) field(DistantScene.class, "ENTRIES").get(null);
        var entry = entries.values().iterator().next();
        var active = (List<RemixInstance>) field(entry.getClass(), "active").get(entry);
        var instances = new ArrayList<Instance>();
        for (int i = 0; i < 40; i++) {
            var instance = new Instance();
            instances.add(instance);
            active.add(instance);
        }
        for (int i = 0; i < 3; i++) {
            DistantScene.beginFrame();
            DistantScene.update(null, Vec3d.ZERO, 2, null);
        }
        assertTrue(entries.isEmpty());
        assertTrue(((Deque<?>) field(DistantScene.class, "RETIRED").get(null)).isEmpty());
        assertTrue(instances.stream().allMatch(RemixInstance::isDeleted));
    }
    @Test
    void emptyReplacementRemovesOldLodWithoutWaitingForAnUpload() throws Exception {
        var old = data(0);
        DistantScene.beginFrame();
        DistantScene.select(old);
        DistantScene.update(null, Vec3d.ZERO, 2, null);
        var entries = (Map<?, ?>) field(DistantScene.class, "ENTRIES").get(null);
        var entry = entries.values().iterator().next();
        var instance = new Instance();
        ((List<RemixInstance>) field(entry.getClass(), "active").get(entry)).add(instance);
        var empty = data(0);
        DistantScene.beginFrame();
        DistantScene.select(empty);
        DistantScene.update(null, Vec3d.ZERO, 2, null);
        assertSame(empty, field(entry.getClass(), "data").get(entry));
        assertEquals(1, instance.hides);
        assertTrue(((List<?>) field(entry.getClass(), "active").get(entry)).isEmpty());
    }
}

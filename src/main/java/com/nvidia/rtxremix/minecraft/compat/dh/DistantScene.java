/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.dh;

import com.nvidia.rtxremix.*;
import com.nvidia.rtxremix.minecraft.asset.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Retains the active DH quadtree in Remix, with bounded mesh uploads and no per-frame geometry rebuild. */
public final class DistantScene {
    private static final Map<DistantLodData.SectionKey, Entry> ENTRIES = new HashMap<>();
    private static final ArrayList<DistantLodData> SELECTED = new ArrayList<>();
    private static final PriorityQueue<Entry> PENDING =
            new PriorityQueue<>(Comparator.comparingDouble(e -> e.distanceSquared));
    private static final ArrayDeque<RemixInstance> RETIRED = new ArrayDeque<>();
    private static final DistantLodMesh DECODER = new DistantLodMesh();
    private static final int UPLOADS = Math.max(1, Integer.getInteger("rtxremix.dh.pagesPerFrame", 2));
    private static RemixMaterial opaque, transparent;
    private static ClientWorld activeWorld;
    private static long frame;
    private DistantScene() {}
    private static final class Entry {
        DistantLodData data;
        ArrayList<RemixInstance> active = new ArrayList<>(), building = new ArrayList<>();
        long seen;
        int nextPage, clipX = Integer.MIN_VALUE, clipZ, clipRadius;
        double distanceSquared;
        DistantLodMesh.Cut cut;
        Entry(DistantLodData data) {
            this.data = data;
        }
    }
    public static void beginFrame() {
        frame++;
        SELECTED.clear();
    }
    public static void select(DistantLodData data) {
        if (data != null && !data.closed)
            SELECTED.add(data);
    }
    public static void update(ClientWorld world, Vec3d camera, int renderDistance, RemixAPI api) {
        if (SELECTED.isEmpty() && ENTRIES.isEmpty() && RETIRED.isEmpty())
            return;
        if (activeWorld != world) {
            releaseEntries(api);
            activeWorld = world;
        }
        int cx = (int) Math.floor(camera.x / 16), cz = (int) Math.floor(camera.z / 16);
        int radius = Math.max(1, renderDistance - 1);
        PENDING.clear();
        for (int i = 0; i < SELECTED.size(); i++) {
            DistantLodData data = SELECTED.get(i);
            if (data.closed)
                continue;
            var key = data.sectionKey;
            Entry entry = ENTRIES.get(key);
            if (entry == null) {
                entry = new Entry(data);
                ENTRIES.put(key, entry);
            } else if (entry.data != data) {
                retire(entry.building, api);
                entry.data = data;
                entry.nextPage = 0;
                if (data.pages.isEmpty()) retire(entry.active, api);
            }
            entry.seen = frame;
            int minX = (cx - radius) * 16, maxX = (cx + radius + 1) * 16, minZ = (cz - radius) * 16,
                maxZ = (cz + radius + 1) * 16;
            boolean intersects = data.x <= maxX && data.x + data.width >= minX && data.z <= maxZ
                    && data.z + data.width >= minZ;
            int clipRadius = intersects ? radius : 0, clipX = intersects ? cx : 0,
                clipZ = intersects ? cz : 0;
            if (entry.clipX != clipX || entry.clipZ != clipZ || entry.clipRadius != clipRadius) {
                retire(entry.building, api);
                entry.nextPage = 0;
                entry.clipX = clipX;
                entry.clipZ = clipZ;
                entry.clipRadius = clipRadius;
                entry.cut = intersects
                        ? new DistantLodMesh.Cut(minX - data.x, maxX - data.x, minZ - data.z, maxZ - data.z)
                        : null;
            }
            if (data.x >= minX && data.x + data.width <= maxX && data.z >= minZ
                    && data.z + data.width <= maxZ) {
                retire(entry.active, api);
                retire(entry.building, api);
                entry.nextPage = data.pages.size();
            }
            double dx = data.x + data.width * .5 - camera.x, dz = data.z + data.width * .5 - camera.z;
            entry.distanceSquared = dx * dx + dz * dz;
            if (entry.nextPage < data.pages.size())
                PENDING.add(entry);
            for (int j = 0; j < entry.active.size(); j++) entry.active.get(j).drawFast(api);
        }
        var iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.seen != frame || entry.data.closed) {
                retire(entry.active, api);
                retire(entry.building, api);
                iterator.remove();
            }
        }
        for (int i = 0; i < 16 && !RETIRED.isEmpty(); i++) destroy(RETIRED.removeFirst());
        com.nvidia.rtxremix.minecraft.profiling.ProfileScope.plot(
                "Java.DH.selectedSections", SELECTED.size());
        com.nvidia.rtxremix.minecraft.profiling.ProfileScope.plot("Java.DH.pendingSections", PENDING.size());
        int uploads = 0;
        while (uploads < UPLOADS && !PENDING.isEmpty()) {
            Entry entry = PENDING.remove();
            if (entry.seen != frame || entry.data.closed)
                continue;
            if (entry.nextPage >= entry.data.pages.size())
                continue;
            var page = entry.data.pages.get(entry.nextPage);
            ensureMaterials();
            var surface = DECODER.decode(page, entry.cut, page.translucent() ? transparent : opaque);
            if (surface != null) {
                RemixMesh mesh = new RemixMesh(new RemixMeshSurface[] {surface});
                RemixInstance instance = null;
                try {
                    RemixMeshStore.get().create(mesh);
                    Transform transform = Transform.identity();
                    transform.matrix[3] = entry.data.x;
                    transform.matrix[7] = entry.data.y;
                    transform.matrix[11] = entry.data.z;
                    instance = new RemixInstance(mesh).setTransform(transform).setDoubleSided(true);
                    RemixInstanceStore.get().create(instance);
                    entry.building.add(instance);
                } catch (RuntimeException e) {
                    if (instance != null && instance.isAlive())
                        instance.destroy();
                    if (mesh.isAlive())
                        mesh.destroy();
                    else
                        mesh.disposeData();
                    throw e;
                }
            }
            entry.nextPage++;
            uploads++;
            if (entry.nextPage == entry.data.pages.size()) {
                retire(entry.active, api);
                var swap = entry.active;
                entry.active = entry.building;
                entry.building = swap;
                for (int j = 0; j < entry.active.size(); j++) entry.active.get(j).drawFast(api);
            } else
                PENDING.add(entry);
        }
    }
    private static void ensureMaterials() {
        if (opaque == null) {
            opaque = new RemixMaterial(MaterialInfo.builder()
                                               .opaqueExt(MaterialInfoOpaqueEXT.builder()
                                                                  .albedoConstant(1, 1, 1)
                                                                  .roughnessConstant(.9f)
                                                                  .build())
                                               .build(),
                    null, null);
            RemixMaterialStore.get().create(opaque);
        }
        if (transparent == null) {
            transparent = new RemixMaterial(
                    MaterialInfo.builder()
                            .opaqueExt(MaterialInfoOpaqueEXT.builder()
                                               .albedoConstant(1, 1, 1)
                                               .roughnessConstant(.65f)
                                               .useDrawCallAlphaState(true)
                                               .alphaTestType(7)
                                               .blendType(MaterialInfoOpaqueEXT.BLEND_TYPE_ALPHA)
                                               .build())
                            .build(),
                    null, null);
            RemixMaterialStore.get().create(transparent);
        }
    }
    private static void retire(ArrayList<RemixInstance> instances, RemixAPI api) {
        for (int i = 0; i < instances.size(); i++) {
            var instance = instances.get(i);
            instance.hideFromScene(api);
            RETIRED.addLast(instance);
        }
        instances.clear();
    }
    private static void destroy(RemixInstance instance) {
        instance.destroy();
        instance.getMesh().destroy();
    }
    public static void hide(RemixAPI api) {
        for (Entry entry : ENTRIES.values())
            for (int i = 0; i < entry.active.size(); i++) entry.active.get(i).hideFromScene(api);
    }
    private static void releaseEntries(RemixAPI api) {
        for (Entry entry : ENTRIES.values()) {
            retire(entry.active, api);
            retire(entry.building, api);
        }
        ENTRIES.clear();
        PENDING.clear();
        while (!RETIRED.isEmpty()) destroy(RETIRED.removeFirst());
    }
    public static void clear(RemixAPI api) {
        releaseEntries(api);
        SELECTED.clear();
        activeWorld = null;
        if (opaque != null)
            opaque.destroy();
        if (transparent != null)
            transparent.destroy();
        opaque = transparent = null;
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.bits;

import com.nvidia.rtxremix.*;
import com.nvidia.rtxremix.minecraft.asset.*;
import com.nvidia.rtxremix.minecraft.compat.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.state.property.Properties;
import java.util.*;

/** Directional headlights live in the common world scene, including Sable parent transforms. */
public final class HeadlampLighting {
    // Client block-entity lifecycle runs on the render thread. Weak keys also release abandoned worlds.
    private static final Set<BlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final IdentityHashMap<BlockEntity, Owner> OWNERS = new IdentityHashMap<>();
    private static final Transform POSE = Transform.identity();
    private static final class Owner {
        final Lamp[] lamps = new Lamp[9];
        long seen;
    }
    private static final class Lamp {
        final LightInfo local = HeadlampPose.definition(), world = HeadlampPose.definition();
        final Transform previous = Transform.identity();
        RemixLight light;
        long state = Long.MIN_VALUE;
        int facing = -1;
    }
    private static long generation;
    private HeadlampLighting() {}
    public static void track(BlockEntity be) {
        if (be instanceof HeadlampAccess && be.getWorld() instanceof ClientWorld)
            LOADED.add(be);
    }
    public static void forget(BlockEntity be) {
        if (be.getWorld() instanceof ClientWorld)
            LOADED.remove(be);
    }
    public static void update(ClientWorld world, RemixAPI api) {
        if (LOADED.isEmpty() && OWNERS.isEmpty())
            return;
        long frame = ++generation;
        int budget = 32;
        for (BlockEntity be : LOADED) {
            if (be.isRemoved() || be.getWorld() != world)
                continue;
            long state = ((HeadlampAccess) be).rtxremix$headlampState();
            var facing = be.getCachedState().get(Properties.FACING);
            Owner owner = OWNERS.computeIfAbsent(be, k -> new Owner());
            owner.seen = frame;
            var pos = be.getPos();
            var spatial = SpatialFrames.find(world, pos.getX() >> 4, pos.getZ() >> 4);
            if (spatial != null && !spatial.rtxremix$isAlive())
                continue;
            for (int slot = 0; slot < 9; slot++) {
                Lamp lamp = owner.lamps[slot];
                if (!HeadlampPose.lit(state, slot)) {
                    if ((state >>> (4 + slot * 6) & 63) == 0 && lamp != null && lamp.light != null
                            && budget > 0) {
                        lamp.light.destroy();
                        owner.lamps[slot] = null;
                        budget--;
                    }
                    continue;
                }
                if (lamp == null) {
                    lamp = new Lamp();
                    owner.lamps[slot] = lamp;
                }
                long color = state >>> (4 + slot * 6) & 63;
                boolean changed = lamp.state != color || lamp.facing != facing.getId();
                if (changed) {
                    HeadlampPose.configure(lamp.local, state, slot, facing);
                    HeadlampPose.configure(lamp.world, state, slot, facing);
                    lamp.state = color;
                    lamp.facing = facing.getId();
                }
                var point = lamp.local.diskExt.position;
                double x = pos.getX() + (double) point.x, y = pos.getY() + (double) point.y,
                       z = pos.getZ() + (double) point.z;
                if (spatial != null)
                    spatial.rtxremix$transform(x, y, z, POSE);
                else {
                    POSE.setIdentity();
                    POSE.matrix[3] = (float) x;
                    POSE.matrix[7] = (float) y;
                    POSE.matrix[11] = (float) z;
                }
                if (lamp.light == null && budget == 0)
                    continue;
                if (lamp.light == null || changed || !Arrays.equals(POSE.matrix, lamp.previous.matrix)) {
                    FrameLightTransform.apply(lamp.local, lamp.world, POSE);
                    if (lamp.light == null) {
                        lamp.light = new RemixLight(lamp.world, null);
                        RemixLightStore.get().create(lamp.light);
                        budget--;
                    } else
                        RemixLightStore.get().update(lamp.light, lamp.world);
                    System.arraycopy(POSE.matrix, 0, lamp.previous.matrix, 0, 12);
                }
                lamp.light.drawFast(api);
            }
        }
        var iterator = OWNERS.values().iterator();
        while (iterator.hasNext()) {
            Owner owner = iterator.next();
            if (owner.seen != frame) {
                destroy(owner);
                iterator.remove();
            }
        }
    }
    private static void destroy(Owner owner) {
        for (Lamp lamp : owner.lamps)
            if (lamp != null && lamp.light != null)
                lamp.light.destroy();
    }
    public static void clear() {
        for (Owner owner : OWNERS.values()) destroy(owner);
        OWNERS.clear();
    }
}

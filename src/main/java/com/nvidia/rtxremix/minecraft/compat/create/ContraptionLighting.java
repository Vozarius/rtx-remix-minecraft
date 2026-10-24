/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.create;

import com.nvidia.rtxremix.LightInfo;
import com.nvidia.rtxremix.Transform;
import com.nvidia.rtxremix.minecraft.asset.RemixLight;
import com.nvidia.rtxremix.minecraft.asset.RemixLightStore;
import com.nvidia.rtxremix.minecraft.compat.ContentSupport;
import com.nvidia.rtxremix.minecraft.compat.FrameLightTransform;
import com.nvidia.rtxremix.minecraft.compat.SpatialFrames;
import com.nvidia.rtxremix.minecraft.light.BlockLightDefinition;
import com.nvidia.rtxremix.minecraft.light.BlockLightRuleManager;
import com.nvidia.rtxremix.minecraft.light.LightManager;
import com.nvidia.rtxremix.minecraft.texture.BlockMaterialCatalog;
import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;
import com.nvidia.rtxremix.content.CopperBeaconBlockEntity;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Contraption lights share the same world scene as their captured geometry, never a private light volume. */
public final class ContraptionLighting {
    private static final IdentityHashMap<AbstractContraptionEntity, Owner> OWNERS = new IdentityHashMap<>();
    private static final MatrixStack MODEL = new MatrixStack();
    private static final Transform ROOT = new Transform(), POSE = new Transform();
    private static final Matrix4f ROOT_MATRIX = new Matrix4f();
    private static final float[] CENTER = {0.5f, 0.5f, 0.5f};
    private static final BlockLightRuleManager.ModelSampler SAMPLER =
            BlockLightRuleManager.fixedCentroid(CENTER);
    private static ClientWorld activeWorld;
    private static long generation;
    private ContraptionLighting() {}

    private static final class Source {
        final BlockPos pos;
        int stateId = -1, luminance = -1;
        final ArrayList<Lamp> lamps = new ArrayList<>();
        Source(BlockPos pos) {
            this.pos = pos;
        }
        void clear() {
            for (Lamp lamp : lamps) lamp.light.destroy();
            lamps.clear();
        }
    }
    private static final class Lamp {
        LightInfo local, world;
        double x, y, z;
        final RemixLight light;
        final Transform previousPose = new Transform();
        boolean positioned, definitionChanged;
        Lamp(LightInfo local, LightInfo world, double x, double y, double z) {
            this.local = local;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            light = new RemixLight(world, null);
            RemixLightStore.get().create(light);
        }
    }
    private static final class Owner {
        final Contraption contraption;
        final ArrayList<Source> sources = new ArrayList<>();
        final BlockMaterialCatalog catalog = BlockMaterialCatalog.get();
        int blockCount;
        long seen;
        Owner(Contraption contraption) {
            this.contraption = contraption;
            blockCount = contraption.getBlocks().size();
            for (var entry : contraption.getBlocks().entrySet()) {
                var state = entry.getValue().state();
                if (state.hasBlockEntity() || BlockMaterialCatalog.get().canEmit(state.getBlock())
                        || BlockLightRuleManager.hasMatchingRules(state))
                    sources.add(new Source(entry.getKey().toImmutable()));
            }
        }
        void clear() {
            for (Source source : sources) source.clear();
        }
    }
    public static void update(ClientWorld world, float delta, LightManager manager) {
        if (activeWorld != world) {
            clear();
            activeWorld = world;
        }
        long frame = ++generation;
        double animationTick =
                com.nvidia.rtxremix.minecraft.entity.CopperBeaconAnimationClock.sample(world, delta);
        for (var raw : world.getEntities()) {
            if (!(raw instanceof AbstractContraptionEntity entity) || !entity.isAlive())
                continue;
            Contraption contraption = entity.getContraption();
            if (contraption == null)
                continue;
            Owner owner = OWNERS.get(entity);
            if (owner == null || owner.contraption != contraption
                    || owner.catalog != BlockMaterialCatalog.get()
                    || owner.blockCount != contraption.getBlocks().size()) {
                if (owner != null)
                    owner.clear();
                owner = new Owner(contraption);
                OWNERS.put(entity, owner);
            }
            owner.seen = frame;
            MODEL.push();
            entity.applyLocalTransforms(MODEL, delta);
            double x = entity.prevX + (entity.getX() - entity.prevX) * delta;
            double y = entity.prevY + (entity.getY() - entity.prevY) * delta;
            double z = entity.prevZ + (entity.getZ() - entity.prevZ) * delta;
            var spatial = SpatialFrames.find(world, (int) Math.floor(x / 16), (int) Math.floor(z / 16));
            if (spatial != null && !spatial.rtxremix$isAlive()) {
                MODEL.pop();
                owner.seen = 0;
                continue;
            }
            if (spatial != null)
                spatial.rtxremix$transform(x, y, z, ROOT);
            else {
                ROOT.setIdentity();
                ROOT.matrix[3] = (float) x;
                ROOT.matrix[7] = (float) y;
                ROOT.matrix[11] = (float) z;
            }
            matrix(ROOT, ROOT_MATRIX);
            ROOT_MATRIX.mul(MODEL.peek().getPositionMatrix());
            MODEL.pop();
            var level = contraption.getOrCreateClientContraptionLazy().getRenderLevel();
            for (int sourceIndex = 0; sourceIndex < owner.sources.size(); sourceIndex++) {
                Source source = owner.sources.get(sourceIndex);
                var state = level.getBlockState(source.pos);
                if (ContentSupport.present()
                        && level.getBlockEntity(source.pos) instanceof CopperBeaconBlockEntity beacon) {
                    if (!beacon.isActive() || beacon.getIntensityMultiplier() <= 0) {
                        source.clear();
                        continue;
                    }
                    Lamp lamp;
                    if (source.lamps.isEmpty()) {
                        lamp = new Lamp(LightManager.newBeaconLightInfo(), LightManager.newBeaconLightInfo(),
                                0, 0, 0);
                        source.lamps.add(lamp);
                    } else
                        lamp = source.lamps.getFirst();
                    if (!manager.updateBeaconLightInfo(beacon, animationTick, lamp.local))
                        source.clear();
                    else {
                        manager.updateBeaconLightInfo(beacon, animationTick, lamp.world);
                        var p = lamp.local.diskExt.position;
                        lamp.x = p.x;
                        lamp.y = p.y;
                        lamp.z = p.z;
                        lamp.definitionChanged = true;
                    }
                } else {
                    int stateId = net.minecraft.block.Block.getRawIdFromState(state);
                    int luminance = state.getLightEmission(level, source.pos);
                    if (source.stateId != stateId || source.luminance != luminance) {
                        source.clear();
                        source.stateId = stateId;
                        source.luminance = luminance;
                        for (var entry : BlockLightRuleManager.resolve(state, source.pos, SAMPLER, luminance)) {
                            source.lamps.add(
                                    new Lamp(BlockLightDefinition.of(entry, BlockTextureManager.get()),
                                            BlockLightDefinition.of(entry, BlockTextureManager.get()),
                                            entry.x(), entry.y(), entry.z()));
                        }
                    }
                }
                if (!source.lamps.isEmpty())
                    manager.markMovingLightOwner(entity.getId());
                for (int lampIndex = 0; lampIndex < source.lamps.size(); lampIndex++) {
                    Lamp lamp = source.lamps.get(lampIndex);
                    pose(ROOT_MATRIX, lamp.x, lamp.y, lamp.z, POSE);
                    if (!lamp.positioned || lamp.definitionChanged
                            || !java.util.Arrays.equals(POSE.matrix, lamp.previousPose.matrix)) {
                        FrameLightTransform.apply(lamp.local, lamp.world, POSE);
                        RemixLightStore.get().update(lamp.light, lamp.world);
                        System.arraycopy(POSE.matrix, 0, lamp.previousPose.matrix, 0, 12);
                        lamp.positioned = true;
                        lamp.definitionChanged = false;
                    }
                    lamp.light.drawFast(com.nvidia.rtxremix.minecraft.RtxRemixMod.getInstance()
                                                .getRenderer()
                                                .getRemixApi());
                }
            }
        }
        var it = OWNERS.values().iterator();
        while (it.hasNext()) {
            Owner owner = it.next();
            if (owner.seen != frame) {
                owner.clear();
                it.remove();
            }
        }
    }
    private static void matrix(Transform t, Matrix4f m) {
        float[] a = t.matrix;
        m.set(a[0], a[4], a[8], 0, a[1], a[5], a[9], 0, a[2], a[6], a[10], 0, a[3], a[7], a[11], 1);
    }
    public static void pose(Matrix4f root, double x, double y, double z, Transform out) {
        float[] a = out.matrix;
        a[0] = root.m00();
        a[1] = root.m10();
        a[2] = root.m20();
        a[3] = (float) (root.m30() + a[0] * x + a[1] * y + a[2] * z);
        a[4] = root.m01();
        a[5] = root.m11();
        a[6] = root.m21();
        a[7] = (float) (root.m31() + a[4] * x + a[5] * y + a[6] * z);
        a[8] = root.m02();
        a[9] = root.m12();
        a[10] = root.m22();
        a[11] = (float) (root.m32() + a[8] * x + a[9] * y + a[10] * z);
    }
    public static void clear() {
        for (Owner owner : OWNERS.values()) owner.clear();
        OWNERS.clear();
        activeWorld = null;
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain;

import java.util.Arrays;
import com.nvidia.rtxremix.minecraft.compat.FrameLightTransform;
import com.nvidia.rtxremix.minecraft.compat.SpatialFrame;
import com.nvidia.rtxremix.minecraft.asset.RemixLightStore;
import com.nvidia.rtxremix.Transform;
import com.nvidia.rtxremix.LightInfo;
import com.nvidia.rtxremix.minecraft.asset.RemixLight;
import com.nvidia.rtxremix.minecraft.light.LightPower;

/**
 * One terrain block light as the draw loop sees it: the native handle plus
 * the position and power the reach cull needs, which the {@link RemixLight}
 * itself no longer holds after creation.
 *
 * <p>Render-thread only. The drawn-frame stamp is written only when the light
 * is visited, so a light skipped wholesale by the column or section gates
 * needs no per-light write and simply reads as not drawn.
 */
public final class TerrainLight {

    private static final double FOUR_PI = 4.0 * Math.PI;
    /**
     * A light drawn last frame stays drawn until its weight falls to 1/1.5 of
     * the threshold (about 1.22x the reach in distance), so a light sitting on
     * the boundary does not flicker.
     */
    private static final float HYSTERESIS = 1.5f;

    public final RemixLight light;
    private SpatialFrame frame;
    private LightInfo sourceInfo, frameInfo;
    private double sourceX, sourceY, sourceZ;
    private final Transform pose = new Transform();
    private final Transform previousPose = new Transform();
    private float x, y, z;
    private float power;
    private long lastDrawnFrame = Long.MIN_VALUE;

    public TerrainLight(RemixLight light) {
        this.light = light;
    }

    /** Records where the light is and how much it emits; called at create and update. */
    public void place(float x, float y, float z, float power) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
    }

    public void attachFrame(SpatialFrame frame,
                            LightInfo source, LightInfo world,
                            double x, double y, double z) {
        this.frame = frame;
        this.sourceInfo = source;
        this.frameInfo = world;
        sourceX = x; sourceY = y; sourceZ = z;
        frame.rtxremix$transform(x, y, z, pose);
        applyFrame();
    }

    public LightInfo frameInfo() { return frameInfo; }

    public void updateFrame() {
        if (frame == null) return;
        frame.rtxremix$transform(sourceX, sourceY, sourceZ, pose);
        if (Arrays.equals(previousPose.matrix, pose.matrix)) return;
        applyFrame();
        RemixLightStore.get().update(light, frameInfo);
    }

    private void applyFrame() {
        FrameLightTransform.apply(sourceInfo, frameInfo, pose);
        x = pose.matrix[3]; y = pose.matrix[7]; z = pose.matrix[11];
        System.arraycopy(pose.matrix, 0, previousPose.matrix, 0, 12);
    }

    public float power() {
        return power;
    }

    public boolean drawnInFrame(long frame) {
        return lastDrawnFrame == frame;
    }

    /**
     * Whether the light's weight at the camera, power / (4 pi d^2), clears the
     * threshold this frame, applying the hysteresis band when it was drawn
     * the previous frame. Records the decision.
     */
    public boolean shouldDraw(double camX, double camY, double camZ, float threshold, long frame) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double distanceSq = Math.max(1.0, dx * dx + dy * dy + dz * dz);
        double weight = power / (FOUR_PI * distanceSq);
        float floor = lastDrawnFrame == frame - 1 ? threshold / HYSTERESIS : threshold;
        boolean draw = weight >= floor;
        if (draw) lastDrawnFrame = frame;
        return draw;
    }

    /**
     * Weight threshold for a light reach in blocks: the weight of the shipped
     * torch at that distance. A light of power P is then drawn out to
     * reach x sqrt(P / torch power).
     */
    public static float thresholdFor(int reachBlocks) {
        double reach = Math.max(1, reachBlocks);
        return (float) (LightPower.torchReference() / (FOUR_PI * reach * reach));
    }
}

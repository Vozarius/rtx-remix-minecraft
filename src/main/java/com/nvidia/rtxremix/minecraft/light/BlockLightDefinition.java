/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.light;
import com.nvidia.rtxremix.*;
import com.nvidia.rtxremix.minecraft.texture.BlockTextureManager;

/** Shared native light definitions for terrain and moving block owners. */
public final class BlockLightDefinition {
    private BlockLightDefinition() {}
    public static LightInfo of(LightEntry entry, BlockTextureManager textures) {
        return buildBlockLightInfo(entry, blockLightRadiance(entry, textures));
    }
    /** Linear RGB radiance after every multiplier, shared by the native light and its power. */
    public record BlockLightRadiance(float r, float g, float b) {}

    public static BlockLightRadiance blockLightRadiance(LightEntry entry, BlockTextureManager tex) {
        float[] blockColor = (tex != null) ? tex.getLightColorForBlock(entry.block())
                                           : BlockTextureManager.FALLBACK_WARM_WHITE;
        float r = entry.hasColor() ? entry.colorR() : blockColor[0];
        float g = entry.hasColor() ? entry.colorG() : blockColor[1];
        float b = entry.hasColor() ? entry.colorB() : blockColor[2];
        float baseIntensity = entry.hasIntensity()
                ? entry.intensity()
                : entry.lightLevel() * BlockLightRuleManager.RADIANCE_PER_LIGHT_LEVEL;
        baseIntensity *= entry.authoredIntensityMultiplier();
        float intensity = BlockLightIntensityControls.apply(entry.key().ruleId(), baseIntensity);
        return new BlockLightRadiance(r * intensity, g * intensity, b * intensity);
    }

    public static LightInfo buildBlockLightInfo(LightEntry entry, BlockLightRadiance radiance) {
        LightInfo.Builder builder = LightInfo.builder().radiance(radiance.r(), radiance.g(), radiance.b());
        return switch (entry.shape()) {
            case SPHERE -> {
                LightInfoSphereEXT.Builder sphere = LightInfoSphereEXT.builder()
                        .position((float) entry.x(), (float) entry.y(), (float) entry.z())
                        .radius(entry.radius())
                        .volumetricRadianceScale(entry.volumetricRadianceScale());
                // Spot lights: only sphere/rect/disk accept shaping in the Remix
                // API, and shapedSphere is currently the only producer of it.
                if (entry.hasShaping()) {
                    sphere.shaping(shapingOf(entry));
                }
                yield builder.sphereExt(sphere.build()).build();
            }
            case CYLINDER -> builder
                    .cylinderExt(LightInfoCylinderEXT.builder()
                            .position((float) entry.x(), (float) entry.y(), (float) entry.z())
                            .radius(entry.radius())
                            .axis(entry.axisX(), entry.axisY(), entry.axisZ())
                            .axisLength(entry.axisLength())
                            .volumetricRadianceScale(entry.volumetricRadianceScale())
                            .build())
                    .build();
            case RECT -> builder
                    .rectExt(LightInfoRectEXT.builder()
                            .position((float) entry.x(), (float) entry.y(), (float) entry.z())
                            .xAxis(entry.xAxisX(), entry.xAxisY(), entry.xAxisZ())
                            .xSize(entry.xSize())
                            .yAxis(entry.yAxisX(), entry.yAxisY(), entry.yAxisZ())
                            .ySize(entry.ySize())
                            .direction(entry.directionX(), entry.directionY(), entry.directionZ())
                            .volumetricRadianceScale(entry.volumetricRadianceScale())
                            .build())
                    .build();
            case DISK -> builder
                    .diskExt(LightInfoDiskEXT.builder()
                            .position((float) entry.x(), (float) entry.y(), (float) entry.z())
                            .xAxis(entry.xAxisX(), entry.xAxisY(), entry.xAxisZ())
                            .xRadius(entry.xSize())
                            .yAxis(entry.yAxisX(), entry.yAxisY(), entry.yAxisZ())
                            .yRadius(entry.ySize())
                            .direction(entry.directionX(), entry.directionY(), entry.directionZ())
                            .volumetricRadianceScale(entry.volumetricRadianceScale())
                            .build())
                    .build();
        };
    }

    private static LightInfoLightShaping shapingOf(LightEntry entry) {
        LightInfoLightShaping shaping = new LightInfoLightShaping();
        shaping.direction = new Float3D(
                entry.shapingDirectionX(), entry.shapingDirectionY(), entry.shapingDirectionZ());
        shaping.coneAngleDegrees = entry.coneAngleDegrees();
        shaping.coneSoftness = entry.coneSoftness();
        shaping.focusExponent = entry.focusExponent();
        return shaping;
    }

}

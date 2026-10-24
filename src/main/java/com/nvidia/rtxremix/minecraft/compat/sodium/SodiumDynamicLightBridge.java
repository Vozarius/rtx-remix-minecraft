/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import com.nvidia.rtxremix.LightInfo;
import com.nvidia.rtxremix.LightInfoSphereEXT;
import com.nvidia.rtxremix.RemixAPI;
import com.nvidia.rtxremix.minecraft.asset.RemixLight;
import com.nvidia.rtxremix.minecraft.asset.RemixLightStore;
import com.nvidia.rtxremix.minecraft.light.BlockLightRuleManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import toni.sodiumdynamiclights.DynamicLightSource;
import toni.sodiumdynamiclights.SodiumDynamicLights;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Captures registered contraption/sublevel emitters through Sodium Dynamic Lights' public API. */
public final class SodiumDynamicLightBridge {
    private static final IdentityHashMap<DynamicLightSource, SourceLight> BY_SOURCE = new IdentityHashMap<>();
    private static final ArrayList<SourceLight> LIGHTS = new ArrayList<>();
    private static DynamicLightSource[] sources = new DynamicLightSource[32];
    private static long frame;

    private SodiumDynamicLightBridge() {}

    public static void update(ClientWorld world, RemixAPI api) {
        var dynamicLights = SodiumDynamicLights.get();
        if (dynamicLights == null || !dynamicLights.config.getDynamicLightsMode().isEnabled()) {
            clear();
            return;
        }
        long now = ++frame;
        sources = ((SodiumLightSources) dynamicLights).rtxremix$copyLightSources(sources);
        for (DynamicLightSource source : sources) {
            if (source == null) break;
            // The ordinary light manager already owns entities' held/fire lights and placed block entities.
            if (source instanceof Entity || source instanceof BlockEntity || source.sdl$getDynamicLightLevel() != world) continue;
            if (source instanceof CapturedTerrainLightSource captured && captured.rtxremix$hasCapturedTerrainLight()) continue;
            SourceLight light = BY_SOURCE.get(source);
            int luminance = source.sdl$getLuminance();
            if (light == null && luminance > 0) {
                light = new SourceLight(source);
                BY_SOURCE.put(source, light);
                LIGHTS.add(light);
            }
            if (light == null) continue;
            light.seen = now;
            if (luminance > 0) light.draw(api, luminance);
        }
        for (int i = LIGHTS.size() - 1; i >= 0; i--) {
            SourceLight light = LIGHTS.get(i);
            if (light.seen == now) continue;
            light.light.destroy();
            BY_SOURCE.remove(light.source);
            LIGHTS.remove(i);
        }
        java.util.Arrays.fill(sources, null);
    }

    public static void clear() {
        for (int i = 0; i < LIGHTS.size(); i++) LIGHTS.get(i).light.destroy();
        LIGHTS.clear();
        BY_SOURCE.clear();
        java.util.Arrays.fill(sources, null);
    }

    private static final class SourceLight {
        final DynamicLightSource source;
        final LightInfo info = LightInfo.builder().sphereExt(LightInfoSphereEXT.builder().radius(.1f).build()).build();
        final RemixLight light = new RemixLight(info, null);
        long seen;
        boolean created;
        int luminance;

        SourceLight(DynamicLightSource source) { this.source = source; }

        void draw(RemixAPI api, int level) {
            float x = (float)source.sdl$getDynamicLightX(), y = (float)source.sdl$getDynamicLightY(), z = (float)source.sdl$getDynamicLightZ();
            var pos = info.sphereExt.position;
            boolean changed = pos.x != x || pos.y != y || pos.z != z || luminance != level;
            pos.x = x; pos.y = y; pos.z = z;
            luminance = level;
            float radiance = level * BlockLightRuleManager.RADIANCE_PER_LIGHT_LEVEL;
            info.radiance.x = info.radiance.y = info.radiance.z = radiance;
            if (!created) { RemixLightStore.get().create(light); created = true; }
            else if (changed) RemixLightStore.get().update(light, info);
            light.drawFast(api);
        }
    }
}

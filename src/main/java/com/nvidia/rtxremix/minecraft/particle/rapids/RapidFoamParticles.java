/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.particle.rapids;

import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;

public final class RapidFoamParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES =
            DeferredRegister.create(RegistryKeys.PARTICLE_TYPE, RtxRemixCommonMod.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAPID_FOAM =
            TYPES.register("rapid_foam", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAPID_FOAM_DOT =
            TYPES.register("rapid_foam_dot", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAPID_MIST =
            TYPES.register("rapid_mist", () -> new SimpleParticleType(false));

    private RapidFoamParticles() {}

    public static void registerTypes(IEventBus modBus) {
        TYPES.register(modBus);
    }

}

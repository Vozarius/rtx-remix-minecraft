/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import com.nvidia.rtxremix.minecraft.block.CopperBeaconScreenHandler;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconUpdatePayload;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlock;
import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlockEntity;
import com.nvidia.rtxremix.minecraft.particle.rapids.RapidFoamParticles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Registers content on both physical sides; starts the renderer only on the client. */
@Mod(RtxRemixCommonMod.LOADER_MOD_ID)
public final class RtxRemixCommonMod {
    public static final String LOADER_MOD_ID = "rtx_remix_renderer";
    /** Resource and save-data namespace, retained for existing worlds and packs. */
    public static final String MOD_ID = "rtx-remix-renderer";

    public RtxRemixCommonMod(IEventBus modBus) {
        CopperBeaconBlock.register(modBus);
        CopperBeaconBlockEntity.register(modBus);
        CopperBeaconScreenHandler.register(modBus);
        RapidFoamParticles.registerTypes(modBus);
        modBus.addListener(CopperBeaconUpdatePayload::register);
    }
}

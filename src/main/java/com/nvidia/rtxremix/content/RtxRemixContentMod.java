/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.content;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Optional physical content. Install on both server and client when using Copper Beacons. */
@Mod("rtx_remix_content")
public final class RtxRemixContentMod {
    public RtxRemixContentMod(IEventBus bus) {
        CopperBeaconBlock.register(bus);
        CopperBeaconBlockEntity.register(bus);
        CopperBeaconScreenHandler.register(bus);
        bus.addListener(CopperBeaconUpdatePayload::register);
    }
}

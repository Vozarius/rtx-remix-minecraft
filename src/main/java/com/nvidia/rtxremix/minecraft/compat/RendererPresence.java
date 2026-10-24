/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

public final class RendererPresence {
    private static final boolean SODIUM = present("net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer.class");
    private static final boolean WORKER_RENDERERS = SODIUM
            || present("dev/ryanhcode/sable/sublevel/ClientSubLevel.class");
    private RendererPresence() {}
    private static boolean present(String resource) { return RendererPresence.class.getClassLoader().getResource(resource) != null; }
    public static boolean hasSodium() { return SODIUM; }
    public static boolean hasWorkerRenderers() { return WORKER_RENDERERS; }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/** Reads the texture stored by a single-texture render phase. */
@Mixin(RenderPhase.Texture.class)
public interface RenderPhaseTextureAccessor {
    @Accessor("id")
    Optional<Identifier> rtxremix$getTextureId();
}

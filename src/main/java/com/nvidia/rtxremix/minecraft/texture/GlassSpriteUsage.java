/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.util.Identifier;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/** A window's shared frame/particle sprites do not change the material of their opaque block owners. */
final class GlassSpriteUsage {
    private final Set<Identifier> candidates = new HashSet<>();
    void add(boolean glass, Set<Identifier> sprites) {
        if (glass)
            candidates.addAll(sprites);
    }
    /** Includes untagged mod windows and their connected-texture atlas variants. */
    void addSprite(Identifier sprite) {
        if (isGlassName(sprite))
            candidates.add(sprite);
    }

    static boolean isGlassName(Identifier id) {
        String path = id.getPath();
        if (!path.startsWith("block/") && !path.startsWith("blocks/"))
            return false;
        for (String token : path.split("[/_\\-]"))
            if (token.equals("glass") || token.equals("window") || token.equals("windows"))
                return true;
        return false;
    }

    Set<Identifier> resolve(Predicate<Identifier> hasTransparency) {
        var result = new HashSet<>(candidates);
        result.removeIf(id -> !hasTransparency.test(id));
        return result;
    }
}

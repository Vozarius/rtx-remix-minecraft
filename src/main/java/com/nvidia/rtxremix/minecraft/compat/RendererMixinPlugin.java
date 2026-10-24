/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;

public final class RendererMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".EntityCullingVisibilityMixin")) {
            return present("dev/tr7zw/entityculling/mixin/CullableMixin.class");
        }
        if (!mixinClassName.contains(".Sodium") && !mixinClassName.contains(".Flywheel") && !mixinClassName.contains(".Sable") && !mixinClassName.contains(".FrameGen")) return true;
        return present(targetClassName.replace('.', '/') + ".class");
    }
    private static boolean present(String resource) {
        try (var stream = MixinService.getService().getResourceAsStream(resource)) {
            return stream != null;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}

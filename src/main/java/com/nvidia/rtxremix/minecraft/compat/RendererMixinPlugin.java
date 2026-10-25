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
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) return false;
        if (mixinClassName.endsWith(".SlopiumSodiumScopeMixin"))
            return present("dev/slopium/visibility/RenderBudget.class")
                    && present(targetClassName.replace('.', '/') + ".class");
        if (mixinClassName.endsWith(".HeadlampLifecycleMixin"))
            return present("com/kipti/bnb/content/trinkets/light/headlamp/HeadlampBlockEntity.class");
        if (mixinClassName.endsWith(".EntityCullingVisibilityMixin")) {
            return present("dev/tr7zw/entityculling/mixin/CullableMixin.class");
        }
        if (!mixinClassName.contains(".Create") && !mixinClassName.contains(".Sodium")
                && !mixinClassName.contains(".Flywheel") && !mixinClassName.contains(".Sable")
                && !mixinClassName.contains(".FrameGen") && !mixinClassName.contains(".Distant")
                && !mixinClassName.contains(".Slopium") && !mixinClassName.contains(".Headlamp"))
            return true;
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

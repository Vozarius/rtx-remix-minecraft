/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.validation;

import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
public class ReferenceMixinVerifier {
    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        var service = (ReferenceMixinService) MixinService.getService();
        var transformer = service.transformer();
        MixinExtrasBootstrap.init();
        Mixins.addConfiguration("reference-renderers.mixins.json");
        if (service.getResourceAsStream("dev/tr7zw/entityculling/mixin/CullableMixin.class") != null)
            Mixins.addConfiguration("reference-culling.mixins.json");
        String[] names = {"com.copycatsplus.copycats.utility.BlockEntityUtils",
                "com.kipti.bnb.content.trinkets.light.headlamp.HeadlampBlockEntity",
                "com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer",
                "com.seibel.distanthorizons.core.render.RenderBufferHandler",
                "com.seibel.distanthorizons.core.render.renderer.LodRenderer",
                "dev.slopium.visibility.CreateWallControl", "dev.slopium.visibility.RenderBudget",
                "net.createmod.catnip.render.FluidRenderHelper",
                "net.createmod.catnip.render.ShadeSeparatingSuperByteBuffer",
                "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask",
                "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer",
                "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",
                "net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkVertexConsumer",
                "net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput",
                "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager",
                "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer",
                "dev.engine_room.flywheel.impl.BackendManagerImpl",
                "dev.ryanhcode.sable.sublevel.ClientSubLevel",
                "dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher",
                "dev.screamix.framegen.client.FrameGenRuntime",
                "toni.sodiumdynamiclights.SodiumDynamicLights",
                "top.leonx.dynlight.sodium.sable.SableDynLightSource", "net.minecraft.world.entity.Entity",
                "net.minecraft.world.level.block.entity.BlockEntity"};
        for (String name : names) {
            if (service.getResourceAsStream(name.replace('.', '/') + ".class") == null) {
                if (args.length == 0)
                    throw new AssertionError("Reference target missing: " + name);
                continue;
            }
            byte[] input;
            try (var in = service.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null)
                    throw new AssertionError(name);
                input = in.readAllBytes();
            }
            byte[] result = transformer.transformClassBytes(name, name, input);
            ClassNode node = new ClassNode();
            new ClassReader(result).accept(node, 0);
            boolean found = node.methods.stream().anyMatch(m -> m.name.contains("rtxremix$"));
            if (args.length == 0 && !found)
                throw new AssertionError("Missing hook in " + name);
            if (args.length != 0 && found)
                throw new AssertionError("Optional hook applied without its renderer: " + name);

            if (args.length == 0 && name.endsWith(".LodRenderer")) {
                var prep = node.methods.stream()
                                   .filter(m
                                           -> m.name.equals("renderTerrain")
                                                   && m.desc.startsWith("(Lcom/seibel/distanthorizons/core/"
                                                           + "render/RenderParams;"))
                                   .findFirst()
                                   .orElseThrow();
                boolean prepares = false;
                for (var instruction : prep.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        if (call.name.contains("rtxremix$replaceDraw"))
                            throw new AssertionError("DH preparation must not be cancelled");
                        if (call.name.equals("buildRenderList"))
                            prepares = true;
                    }
                }
                if (!prepares)
                    throw new AssertionError("DH preparation no longer selects LOD sections");
            }
            System.out.println("PASS " + name);
        }
    }
}

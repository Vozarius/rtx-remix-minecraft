/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import com.nvidia.rtxremix.minecraft.block.CopperBeaconBlock;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Registry objects must be constructed by deferred factories, after the mod constructor returns. */
class NeoForgeRegistrationTest {
    private static final String MOD_PACKAGE = "com/nvidia/rtxremix/minecraft/";
    private static final Set<String> REGISTRY_TYPES = Set.of(
            "net/minecraft/block/Block", "net/minecraft/item/Item",
            "net/minecraft/block/entity/BlockEntityType", "net/minecraft/particle/ParticleType",
            "net/minecraft/screen/ScreenHandlerType", "net/minecraft/entity/EntityType");

    @Test
    void commonConstructorDoesNotCreateRegistryObjectsOrResolveHolders() throws IOException {
        Scan scan = new Scan();
        scan.visit(MOD_PACKAGE + "RtxRemixCommonMod", "<init>", "(Lnet/neoforged/bus/api/IEventBus;)V");
        assertEquals(Set.of(), scan.violations);
    }

    @Test
    void detectsEagerConstructionThroughAHelper() throws IOException {
        Scan scan = new Scan();
        scan.initialize(MOD_PACKAGE + "NeoForgeRegistrationTest$EagerContent");
        assertFalse(scan.violations.isEmpty());
    }

    @Test
    void leavesDeferredFactoriesUncalled() throws IOException {
        Scan scan = new Scan();
        scan.initialize(MOD_PACKAGE + "NeoForgeRegistrationTest$DeferredContent");
        assertEquals(Set.of(), scan.violations);
    }

    private static final class EagerContent {
        private static final CopperBeaconBlock BLOCK = create();

        private static CopperBeaconBlock create() {
            return new CopperBeaconBlock(null);
        }
    }

    private static final class DeferredContent {
        private static final Supplier<CopperBeaconBlock> BLOCK = () -> new CopperBeaconBlock(null);
    }

    private static final class Scan {
        private final Map<String, ClassNode> classes = new HashMap<>();
        private final Set<String> visited = new HashSet<>();
        private final Set<String> initialized = new HashSet<>();
        private final Set<String> violations = new TreeSet<>();

        private ClassNode read(String name) throws IOException {
            if (classes.containsKey(name)) return classes.get(name);
            try (var input = getClass().getResourceAsStream("/" + name + ".class")) {
                assertNotNull(input, "Missing class " + name);
                ClassNode node = new ClassNode();
                new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                classes.put(name, node);
                return node;
            }
        }

        private void initialize(String owner) throws IOException {
            if (!owner.startsWith(MOD_PACKAGE) || !initialized.add(owner)) return;
            String parent = read(owner).superName;
            if (parent != null) initialize(parent);
            visit(owner, "<clinit>", "()V");
        }

        private boolean registryObject(String name) throws IOException {
            while (name != null && !name.equals("java/lang/Object")) {
                if (REGISTRY_TYPES.contains(name)) return true;
                name = read(name).superName;
            }
            return false;
        }

        private void visit(String owner, String name, String descriptor) throws IOException {
            if (!owner.startsWith(MOD_PACKAGE) || !visited.add(owner + "." + name + descriptor)) return;
            initialize(owner);
            for (var method : read(owner).methods) {
                if (!method.name.equals(name) || !method.desc.equals(descriptor)) continue;
                for (var instruction : method.instructions) {
                    if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW) {
                        if (registryObject(allocation.desc)) {
                            violations.add(owner + "." + name + " constructs " + allocation.desc);
                        }
                        initialize(allocation.desc);
                    } else if (instruction instanceof MethodInsnNode call) {
                        if (call.owner.startsWith("net/neoforged/neoforge/registries/Deferred")
                                && call.name.equals("get")) {
                            violations.add(owner + "." + name + " resolves a deferred holder");
                        }
                        visit(call.owner, call.name, call.desc);
                    } else if (instruction instanceof FieldInsnNode field
                            && (field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC)) {
                        initialize(field.owner);
                    }
                    // Lambda bodies and event listeners are not invoked during registration setup.
                }
            }
        }
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Checks production names and access flags; development access widening can hide packaging errors. */
class NeoForgePackagedAccessTest {
    @Test
    void remappedMinecraftCallsAreAccessibleWithPackagedTransformers() throws IOException {
        try (Audit audit = new Audit()) {
            // NeoForge's own transformers are present on the test classpath.
            var configs = getClass().getClassLoader().getResources("META-INF/accesstransformer.cfg");
            while (configs.hasMoreElements()) {
                try (var input = configs.nextElement().openStream()) {
                    audit.transform(new String(input.readAllBytes(), StandardCharsets.UTF_8), false);
                }
            }
            var config = audit.mod.getJarEntry("META-INF/accesstransformer.cfg");
            assertNotNull(config, "The distribution must include its access transformer");
            try (var input = audit.mod.getInputStream(config)) {
                audit.transform(new String(input.readAllBytes(), StandardCharsets.UTF_8), true);
            }
            audit.checkCalls();
            assertEquals(Set.of(), audit.failures, "Invalid production access to Minecraft");
        }
    }

    private static final class Audit implements AutoCloseable {
        private final JarFile mod;
        private final List<JarFile> jars = new ArrayList<>();
        private final Map<String, ClassNode> classes = new HashMap<>();
        private final Set<String> failures = new TreeSet<>();

        private Audit() throws IOException {
            mod = new JarFile(System.getProperty("rtxremix.remappedJar"));
            jars.add(mod);
            for (String path : System.getProperty("rtxremix.mojangJars").split(Pattern.quote(File.pathSeparator))) {
                jars.add(new JarFile(path));
            }
        }

        private ClassNode read(String name) throws IOException {
            if (classes.containsKey(name)) return classes.get(name);
            ClassNode node = null;
            for (JarFile jar : jars) {
                var entry = jar.getJarEntry(name + ".class");
                if (entry == null) continue;
                try (var input = jar.getInputStream(entry)) {
                    node = new ClassNode();
                    new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                break;
            }
            if (node == null && name.startsWith("java/")) {
                try (var input = ClassLoader.getSystemResourceAsStream(name + ".class")) {
                    if (input != null) {
                        node = new ClassNode();
                        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    }
                }
            }
            classes.put(name, node);
            return node;
        }

        private static int access(int old, String directive) {
            int result = old & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
            if (directive.startsWith("public")) result |= Opcodes.ACC_PUBLIC;
            else if (directive.startsWith("protected")) result |= Opcodes.ACC_PROTECTED;
            else if (directive.startsWith("private")) result |= Opcodes.ACC_PRIVATE;
            if (directive.endsWith("-f")) result &= ~Opcodes.ACC_FINAL;
            else if (directive.endsWith("+f")) result |= Opcodes.ACC_FINAL;
            return result;
        }

        private void transform(String text, boolean validate) throws IOException {
            for (String raw : text.split("\n")) {
                String line = raw.split("#", 2)[0].trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                ClassNode node = read(parts[1].replace('.', '/'));
                if (node == null) {
                    if (validate) failures.add("AT class missing: " + line);
                    continue;
                }
                if (parts.length == 2) {
                    node.access = access(node.access, parts[0]);
                    continue;
                }
                String selector = parts[2];
                boolean found = false;
                if (selector.contains("(")) {
                    for (var method : node.methods) {
                        if (!(method.name + method.desc).equals(selector) && !selector.equals("*()")) continue;
                        method.access = access(method.access, parts[0]);
                        found = true;
                    }
                } else {
                    for (var field : node.fields) {
                        if (!field.name.equals(selector) && !selector.equals("*")) continue;
                        field.access = access(field.access, parts[0]);
                        found = true;
                    }
                }
                if (!found && validate) failures.add("AT member missing: " + line);
            }
        }

        private record Member(String owner, int access) {}

        private Member resolve(String owner, String name, String descriptor, boolean field,
                               Set<String> visited) throws IOException {
            if (!visited.add(owner)) return null;
            ClassNode node = read(owner);
            if (node == null) return null;
            if (field) {
                for (var candidate : node.fields) {
                    if (candidate.name.equals(name) && candidate.desc.equals(descriptor)) {
                        return new Member(owner, candidate.access);
                    }
                }
            } else {
                for (var candidate : node.methods) {
                    if (candidate.name.equals(name) && candidate.desc.equals(descriptor)) {
                        return new Member(owner, candidate.access);
                    }
                }
            }
            if (name.equals("<init>")) return null;
            Member result = node.superName == null ? null
                    : resolve(node.superName, name, descriptor, field, visited);
            if (result != null) return result;
            for (String parent : node.interfaces) {
                result = resolve(parent, name, descriptor, field, visited);
                if (result != null) return result;
            }
            return null;
        }

        private boolean subclass(String child, String parent, Set<String> visited) throws IOException {
            if (child.equals(parent)) return true;
            if (!visited.add(child)) return false;
            ClassNode node = read(child);
            if (node == null) return false;
            if (node.superName != null && subclass(node.superName, parent, visited)) return true;
            for (String type : node.interfaces) {
                if (subclass(type, parent, visited)) return true;
            }
            return false;
        }

        private boolean allowed(String caller, String owner, int flags) throws IOException {
            if ((flags & Opcodes.ACC_PUBLIC) != 0 || caller.equals(owner)) return true;
            if ((flags & Opcodes.ACC_PRIVATE) != 0) return false;
            String callerPackage = caller.substring(0, caller.lastIndexOf('/'));
            String ownerPackage = owner.substring(0, owner.lastIndexOf('/'));
            if (callerPackage.equals(ownerPackage)) return true;
            return (flags & Opcodes.ACC_PROTECTED) != 0 && subclass(caller, owner, new HashSet<>());
        }

        private void check(String caller, String where, String owner, String name,
                           String descriptor, boolean field) throws IOException {
            if (!owner.startsWith("net/minecraft/") && !owner.startsWith("com/mojang/blaze3d/")) return;
            ClassNode type = read(owner);
            assertNotNull(type, "Minecraft class missing: " + owner);
            if (!allowed(caller, owner, type.access)) failures.add(where + " inaccessible class " + owner);
            Member member = resolve(owner, name, descriptor, field, new HashSet<>());
            if (member == null) failures.add(where + " missing " + owner + "." + name + descriptor);
            else if (!allowed(caller, member.owner, member.access)) {
                failures.add(where + " inaccessible " + member.owner + "." + name + descriptor);
            }
        }

        private void checkCalls() throws IOException {
            for (var entry : Collections.list(mod.entries())) {
                String path = entry.getName();
                // Mixins execute inside their targets, with those targets' access privileges.
                if (!path.endsWith(".class") || path.contains("/mixin/")) continue;
                ClassNode node = read(path.substring(0, path.length() - 6));
                for (var method : node.methods) {
                    String where = node.name + "." + method.name;
                    for (var instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode call) {
                            check(node.name, where, call.owner, call.name, call.desc, false);
                        } else if (instruction instanceof FieldInsnNode field) {
                            check(node.name, where, field.owner, field.name, field.desc, true);
                        }
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            for (JarFile jar : jars) jar.close();
        }
    }
}

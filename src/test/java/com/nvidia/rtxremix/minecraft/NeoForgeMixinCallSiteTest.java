/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Checks required invocation anchors against NeoForge's patched bytecode, including loader and library calls. */
class NeoForgeMixinCallSiteTest {
    @Test
    void requiredInvocationAnchorsExistInPatchedTargets() throws IOException {
        Set<String> missing = new TreeSet<>();
        Path directory = Path.of("build/classes/java/main/com/nvidia/rtxremix/minecraft/mixin");
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassNode mixin = readClass(Files.newInputStream(file));
                for (AnnotationNode declaration : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
                    if (!declaration.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    List<String> targets = new ArrayList<>();
                    for (Object type : list(value(declaration, "value"))) {
                        targets.add(((Type) type).getInternalName());
                    }
                    for (Object target : list(value(declaration, "targets"))) {
                        targets.add(target.toString().replace('.', '/'));
                    }
                    for (String target : targets) {
                        InputStream resource = getClass().getResourceAsStream("/" + target + ".class");
                        boolean optional = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                                .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;"));
                        if (resource == null && optional) continue;
                        ClassNode targetClass = readClass(resource);
                        checkAnchors(mixin, targetClass, missing);
                    }
                }
            }
        }
        assertEquals(Set.of(), missing, "Required mixin invocation anchors missing from patched targets");
    }

    private static void checkAnchors(ClassNode mixin, ClassNode targetClass, Set<String> missing) {
        for (MethodNode handler : mixin.methods) {
            for (AnnotationNode injection : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                if (Integer.valueOf(0).equals(value(injection, "require"))) continue;
                List<?> selectors = list(value(injection, "method"));
                if (selectors.isEmpty()) continue;
                for (Object object : list(value(injection, "at"))) {
                    AnnotationNode at = (AnnotationNode) object;
                    Object kind = value(at, "value");
                    if (kind == null || !kind.toString().startsWith("INVOKE")) continue;
                    String target = (String) value(at, "target");
                    if (target == null || !target.startsWith("L")) continue;
                    int endOwner = target.indexOf(';');
                    int arguments = target.indexOf('(', endOwner);
                    String owner = target.substring(1, endOwner);
                    String name = target.substring(endOwner + 1, arguments);
                    String descriptor = target.substring(arguments);
                    int matches = 0;
                    for (MethodNode method : targetClass.methods) {
                        if (!selected(method, selectors)) continue;
                        for (var instruction : method.instructions) {
                            if (instruction instanceof MethodInsnNode call
                                    && call.owner.equals(owner) && call.name.equals(name)
                                    && call.desc.equals(descriptor)) matches++;
                        }
                    }
                    Integer ordinal = (Integer) value(at, "ordinal");
                    if (matches == 0 || (ordinal != null && ordinal >= matches)) {
                        missing.add(mixin.name + "." + handler.name + " -> " + target);
                    }
                }
            }
        }
    }

    private static boolean selected(MethodNode method, List<?> selectors) {
        for (Object selector : selectors) {
            String text = selector.toString();
            int arguments = text.indexOf('(');
            if (arguments < 0 ? method.name.equals(text)
                    : (method.name + method.desc).equals(text)) return true;
        }
        return false;
    }

    private static ClassNode readClass(InputStream stream) throws IOException {
        assertNotNull(stream, "Missing class resource");
        try (stream) {
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : value == null ? List.of() : List.of(value);
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import java.net.URL;
import java.net.URLClassLoader;
import static org.junit.jupiter.api.Assertions.*;

/** Registry/network ownership is checked against the actual distributable class sets. */
class PureClientArtifactTest {
    @Test
    void rendererContainsNoContentClassesOrRegistryRegistration() throws Exception {
        try (var jar = new ZipFile(System.getProperty("rtxremix.clientJar"))) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                assertFalse(e.getName().startsWith("com/nvidia/rtxremix/content/"), e.getName());
                assertFalse(e.getName().startsWith("data/"), e.getName());
                if (!e.getName().endsWith(".class"))
                    continue;
                String bytes = new String(jar.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("neoforge/registries/DeferredRegister"), e.getName());
                assertFalse(bytes.contains("network/event/RegisterPayloadHandlersEvent"), e.getName());
            }
        }
    }
    @Test
    void contentNeedsNeitherTheRendererNorNativeRuntimeOnTheServer() throws Exception {
        try (var jar = new ZipFile(System.getProperty("rtxremix.contentJar"))) {
            assertNull(jar.getEntry("native/"));
            assertNotNull(jar.getEntry("com/nvidia/rtxremix/content/RtxRemixContentMod.class"));
            assertNotNull(jar.getEntry("data/minecraft/tags/block/mineable/pickaxe.json"));
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var e = entries.nextElement();
                if (!e.getName().endsWith(".class"))
                    continue;
                String bytes = new String(jar.getInputStream(e).readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("com/nvidia/rtxremix/minecraft/"), e.getName());
                assertFalse(bytes.contains("net/minecraft/client/"), e.getName());
            }
        }
    }
    @Test
    void coreLightManagerCanInitializeWithoutAnyContentAddonClasses() throws Exception {
        URL[] urls =
                java.util.Arrays
                        .stream(java.nio.file.Files
                                        .readString(Path.of(
                                                System.getProperty("rtxremix.namedTestClasspathFile")))
                                        .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                        .map(s -> {
                            try {
                                return Path.of(s).toUri().toURL();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .toArray(URL[] ::new);
        try (var loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.nvidia.rtxremix.content."))
                    throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
            @Override
            public URL getResource(String name) {
                return name.startsWith("com/nvidia/rtxremix/content/") ? null : super.getResource(name);
            }
        }) {
            Class<?> support =
                    Class.forName("com.nvidia.rtxremix.minecraft.compat.ContentSupport", true, loader);
            assertEquals(false, support.getMethod("present").invoke(null));
            Class<?> renderer = Class.forName("com.nvidia.rtxremix.minecraft.RemixRenderer", false, loader);
            Class<?> manager =
                    Class.forName("com.nvidia.rtxremix.minecraft.light.LightManager", true, loader);
            assertNotNull(manager.getDeclaredConstructor(renderer).newInstance((Object) null));
        }
    }
}

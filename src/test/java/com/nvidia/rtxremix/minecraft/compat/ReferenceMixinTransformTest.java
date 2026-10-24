/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Applies production mixins to the actual reference binaries in an isolated JVM. */
class ReferenceMixinTransformTest {
    @Test void transformsInstalledReferenceRenderers() throws Exception { verify(true); }
    @Test void optionalAdaptersDoNotRequireReferenceMods() throws Exception { verify(false); }

    private static void verify(boolean reference) throws Exception {
        Path directory = Path.of("build/reference-verification");
        Files.createDirectories(directory);
        String mode = reference ? "installed" : "absent";
        Path arguments = directory.resolve(mode + ".args");
        Path log = directory.resolve(mode + ".log");
        String classpath = System.getProperty("rtxremix.referenceVerifierClasspath")
                + (reference ? java.io.File.pathSeparator + System.getProperty("rtxremix.referenceJars") : "");
        Files.writeString(arguments, "-Dmixin.service=com.nvidia.rtxremix.minecraft.compat.validation.ReferenceMixinService\n"
                + "-classpath\n\"" + classpath.replace('\\','/') + "\"\n"
                + "com.nvidia.rtxremix.minecraft.compat.validation.ReferenceMixinVerifier\n"
                + (reference ? "" : "without-reference\n"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "@" + arguments.toAbsolutePath()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(90, TimeUnit.SECONDS), "Reference mixin verification timed out: " + log);
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(log); } catch (Exception e) { return log.toString(); }
            });
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}

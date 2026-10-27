/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.util.Identifier;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceAlphaTest {
    @Test
    void everyCreateDecoLadderHasSolidMetalAndFullyOpenHoles() throws Exception {
        int checked = 0;
        try (var jar = new ZipFile("reference/createdeco-2.1.3.jar")) {
            for (var entry : jar.stream()
                                     .filter(e
                                             -> e.getName().startsWith(
                                                        "assets/createdeco/textures/block/palettes/ladders/")
                                                     && e.getName().endsWith(".png"))
                                     .toList()) {
                var image = ImageIO.read(jar.getInputStream(entry));
                var alpha = AlphaProfile.scan(image.getWidth(), image.getHeight(), image.getWidth(),
                        image.getHeight(), image::getRGB);
                assertFalse(alpha.fractional(), entry.getName());
                assertFalse(alpha.transparent().isEmpty(), entry.getName());
                assertTrue(alpha.transparent().cardinality() < image.getWidth() * image.getHeight());
                assertEquals("cutout", BlockSurfaceLayer.resolve("translucent", false, true, false));
                checked++;
            }
        }
        assertEquals(10, checked);
    }

    @Test
    void transparentWindowsIncludingRailwaysAndConnectedVariantsAreRecognized() throws Exception {
        int windows = 0;
        for (String filename : new String[] {"create-1.21.1-6.0.11.jar", "createdeco-2.1.3.jar",
                     "railways-0.1.0-beta+neoforge-mc1.21.1.jar"}) {
            // Railways may use a versioned filename; select the installed reference archive.
            Path path = Path.of("reference", filename);
            if (!java.nio.file.Files.exists(path)) {
                try (var files = java.nio.file.Files.list(Path.of("reference"))) {
                    path = files.filter(p
                                        -> p.getFileName().toString().startsWith("railways-")
                                                && p.toString().endsWith(".jar"))
                                   .findFirst()
                                   .orElseThrow();
                }
            }
            try (var jar = new ZipFile(path.toFile())) {
                for (var entry : jar.stream()
                                         .filter(e
                                                 -> e.getName().contains("/textures/block/")
                                                         && e.getName().endsWith(".png"))
                                         .toList()) {
                    String name = entry.getName();
                    String[] parts = name.split("/", 4);
                    Identifier sprite = Identifier.tryParse(
                            parts[1] + ":" + parts[3].substring(0, parts[3].length() - 4));
                    // Invalid development-only PNG names are never registered in Minecraft's atlas.
                    if (sprite == null || !GlassSpriteUsage.isGlassName(sprite))
                        continue;
                    var image = ImageIO.read(jar.getInputStream(entry));
                    var alpha = AlphaProfile.scan(image.getWidth(), image.getHeight(), image.getWidth(),
                            image.getHeight(), image::getRGB);
                    if (alpha.transparent().isEmpty())
                        continue;
                    var usage = new GlassSpriteUsage();
                    usage.addSprite(sprite);
                    assertEquals(Set.of(sprite), usage.resolve(id -> true), sprite.toString());
                    assertEquals(
                            "glass", BlockSurfaceLayer.resolve("cutout", true, true, alpha.fractional()));
                    windows++;
                }
            }
        }
        assertTrue(windows >= 150,
                "Expected base and connected window sprites across all three mods: " + windows);
        assertFalse(GlassSpriteUsage.isGlassName(Identifier.of("create", "block/ladder_andesite")));
        assertFalse(GlassSpriteUsage.isGlassName(Identifier.ofVanilla("block/oak_planks")));
        assertFalse(GlassSpriteUsage.isGlassName(Identifier.ofVanilla("item/glass_bottle")));
    }

    @Test
    void laterAnimationFramesCanIntroduceFractionalAlpha() {
        int[][] frames = {{0xffabcdef, 0xffabcdef}, {0x80abcdef, 0x00abcdef}};
        var profile = AlphaProfile.scan(2, 2, 2, 1, (x, y) -> frames[y][x]);
        assertTrue(profile.fractional());
        assertEquals(2, profile.transparent().cardinality());
    }
}

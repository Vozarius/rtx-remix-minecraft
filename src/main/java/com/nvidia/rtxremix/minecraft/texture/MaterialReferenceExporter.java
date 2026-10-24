/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Exports the active pack's original images without overwriting an author's work. */
final class MaterialReferenceExporter {
    private MaterialReferenceExporter() {}
    static void export(
            MinecraftClient client, SpriteAtlasTexture atlas, LabPBRConverter.VariantCache variants) {
        Path root = Path.of(System.getProperty("rtxremix.materialReferenceDirectory",
                client.runDirectory.toPath().resolve("rtx-remix/reference/materials").toString()));
        var catalog = BlockMaterialCatalog.get();
        var rows = new ArrayList<Object>();
        var ids = new java.util.TreeSet<Identifier>(java.util.Comparator.comparing(Identifier::toString));
        ids.addAll(catalog.luminousSprites().keySet());
        ids.addAll(catalog.fluidSprites().keySet());
        try {
            for (Identifier id : ids) {
                Identifier resourceId = Identifier.of(id.getNamespace(), "textures/" + id.getPath() + ".png");
                var resource = client.getResourceManager().getResource(resourceId);
                if (resource.isEmpty())
                    continue;
                Path file = root.resolve(
                        "sources/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
                Files.createDirectories(file.getParent());
                if (!Files.exists(file))
                    try (var stream = resource.get().getInputStream()) {
                        Files.copy(stream, file);
                    }
                var row = new LinkedHashMap<String, Object>();
                row.put("sprite", id.toString());
                row.put("source", root.relativize(file).toString().replace('\\', '/'));
                row.put("luminous_blocks",
                        catalog.luminousSprites()
                                .getOrDefault(id, java.util.Set.of())
                                .stream()
                                .map(Identifier::toString)
                                .sorted()
                                .toList());
                try (var mask = variants.loadVariant(id, "_e");
                        var specular = variants.loadVariant(id, "_s")) {
                    row.put("needs_emission_mask",
                            catalog.luminousSprites().containsKey(id) && mask == null
                                    && !(specular != null && LabPBRConverter.hasEmissiveData(specular))
                                    && BuiltinEmission.mask(id) == null);
                }
                row.put("mask_path", "assets/" + id.getNamespace() + "/textures/" + id.getPath() + "_e.png");
                var fluid = catalog.fluid(id);
                if (fluid != null) {
                    row.put("fluid", Registries.FLUID.getId(fluid).toString());
                    row.put("tint_argb",
                            String.format("%08x", IClientFluidTypeExtensions.of(fluid).getTintColor()));
                    try (var stream = resource.get().getInputStream();
                            var image = net.minecraft.client.texture.NativeImage.read(stream)) {
                        byte[] rgba = new byte[image.getWidth() * image.getHeight() * 4];
                        for (int y = 0; y < image.getHeight(); y++)
                            for (int x = 0; x < image.getWidth(); x++) {
                                int p = image.getColor(x, y), i = (y * image.getWidth() + x) * 4;
                                rgba[i] = (byte) p;
                                rgba[i + 1] = (byte) (p >>> 8);
                                rgba[i + 2] = (byte) (p >>> 16);
                                rgba[i + 3] = (byte) (p >>> 24);
                            }
                        row.put("texture_average", TextureAverage.of(rgba));
                    }
                }
                rows.add(row);
            }
            Files.createDirectories(root);
            Files.writeString(root.resolve("catalog.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(rows));
            LoggerFactory.getLogger(MaterialReferenceExporter.class)
                    .info("Material reference exported to {} ({} sprites)", root, rows.size());
        } catch (IOException e) {
            LoggerFactory.getLogger(MaterialReferenceExporter.class)
                    .warn("Cannot export material reference to {}", root, e);
        }
    }
}

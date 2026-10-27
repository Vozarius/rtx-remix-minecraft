/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.block.TransparentBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassPaneBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** One resource-reload scan of registered block states and fluid types; workers only read its snapshot. */
public final class BlockMaterialCatalog {
    private static final TagKey<Block> GLASS_BLOCKS =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of("c", "glass_blocks"));
    private static final TagKey<Block> GLASS_PANES =
            TagKey.of(RegistryKeys.BLOCK, Identifier.of("c", "glass_panes"));
    private static volatile BlockMaterialCatalog active = new BlockMaterialCatalog();
    private final Map<BlockState, Set<Identifier>> stateSprites = new IdentityHashMap<>();
    private final Map<BlockState, EmissionProfile> emission = new IdentityHashMap<>();
    private final Map<Block, Set<Identifier>> blockSprites = new IdentityHashMap<>();
    private final Map<Identifier, Set<Identifier>> luminousSprites = new HashMap<>();
    private final Set<Block> glassBlocks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Block> potentialEmitters = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Identifier> glassSprites = new HashSet<>();
    public record FluidProfile(
            Identifier id, Identifier still, Identifier flow, Identifier overlay, Fluid fluid) {}
    private final Map<Fluid, FluidProfile> fluidProfiles = new IdentityHashMap<>();
    public Map<Fluid, FluidProfile> fluidProfiles() {
        return java.util.Collections.unmodifiableMap(fluidProfiles);
    }
    private final Map<Identifier, Fluid> fluidSprites = new HashMap<>();

    private BlockMaterialCatalog() {}
    public static BlockMaterialCatalog get() {
        return active;
    }

    public static void rebuild(MinecraftClient client, BlockTextureManager textures) {
        BlockMaterialCatalog next = new BlockMaterialCatalog();
        Random random = Random.create(42);
        GlassSpriteUsage glassUsage = new GlassSpriteUsage();
        for (Block block : Registries.BLOCK) {
            Set<Identifier> allSprites = new HashSet<>();
            boolean glass = glass(block.getDefaultState());
            if (glass)
                next.glassBlocks.add(block);
            for (BlockState state : block.getStateManager().getStates()) {
                BakedModel model = client.getBlockRenderManager().getModel(state);
                Set<Identifier> ids = new HashSet<>();
                var modelEmission = textures.modelEmission();
                collect(model, state, null, random, ids, modelEmission);
                for (Direction side : Direction.values())
                    collect(model, state, side, random, ids, modelEmission);
                ids.remove(Identifier.ofVanilla("missingno"));
                next.stateSprites.put(state, Set.copyOf(ids));
                allSprites.addAll(ids);
                EmissionProfile profile = modelEmission.build();
                if (profile != null)
                    next.emission.put(state, profile);
                if (state.getLuminance() > 0 || (profile != null && profile.strength() > 0)) {
                    next.potentialEmitters.add(block);
                    for (Identifier id : ids)
                        next.luminousSprites.computeIfAbsent(id, k -> new HashSet<>())
                                .add(Registries.BLOCK.getId(block));
                }
            }
            allSprites.add(client.getBlockRenderManager()
                                   .getModel(block.getDefaultState())
                                   .getParticleSprite()
                                   .getContents()
                                   .getId());
            next.blockSprites.put(block, Set.copyOf(allSprites));
            textures.cacheLightColor(block, allSprites);
            glassUsage.add(glass, allSprites);
        }
        var atlas = (net.minecraft.client.texture.SpriteAtlasTexture) client.getTextureManager().getTexture(
                BlockTextureManager.BLOCK_ATLAS_ID);
        for (Identifier id :
                ((com.nvidia.rtxremix.minecraft.mixin.SpriteAtlasAccessor) atlas).getSprites().keySet()) {
            glassUsage.addSprite(id);
        }
        next.glassSprites.addAll(glassUsage.resolve(id -> {
            var entry = textures.getSpriteById(id);
            return entry != null && entry.hasTransparency;
        }));
        for (Fluid fluid : Registries.FLUID) {
            if (fluid == Fluids.EMPTY)
                continue;
            IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
            Identifier still = ext.getStillTexture(), flow = ext.getFlowingTexture();
            if (still != null)
                next.fluidSprites.put(still, fluid);
            if (flow != null)
                next.fluidSprites.put(flow, fluid);
            Identifier overlay = ext.getOverlayTexture();
            if (overlay != null)
                next.fluidSprites.put(overlay, fluid);
            next.fluidProfiles.put(
                    fluid, new FluidProfile(Registries.FLUID.getId(fluid), still, flow, overlay, fluid));
        }
        active = next;
    }

    private static void collect(BakedModel model, BlockState state, Direction side, Random random,
            Set<Identifier> ids, BlockTextureManager.ModelEmission emission) {
        random.setSeed(42);
        for (var quad : model.getQuads(state, side, random)) {
            ids.add(quad.getSprite().getContents().getId());
            emission.add(quad);
        }
    }

    public static boolean glass(BlockState state) {
        Block block = state.getBlock();
        return block instanceof TransparentBlock || block instanceof StainedGlassPaneBlock
                || block == Blocks.GLASS_PANE || state.isIn(GLASS_BLOCKS) || state.isIn(GLASS_PANES);
    }
    public boolean isGlassSprite(Identifier id) {
        return glassSprites.contains(id);
    }
    public Fluid fluid(Identifier sprite) {
        return fluidSprites.get(sprite);
    }
    public EmissionProfile emission(BlockState state) {
        return emission.get(state);
    }
    public boolean canEmit(Block block) {
        return potentialEmitters.contains(block);
    }
    public Map<Identifier, Set<Identifier>> luminousSprites() {
        return java.util.Collections.unmodifiableMap(luminousSprites);
    }
    public Set<Identifier> sprites(Block block) {
        return blockSprites.getOrDefault(block, Set.of());
    }
    public Set<Identifier> sprites(BlockState state) {
        return stateSprites.getOrDefault(state, Set.of());
    }
    public Map<Identifier, Fluid> fluidSprites() {
        return java.util.Collections.unmodifiableMap(fluidSprites);
    }
}

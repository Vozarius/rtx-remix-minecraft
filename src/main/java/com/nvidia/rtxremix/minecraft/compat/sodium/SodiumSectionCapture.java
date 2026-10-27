/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.sodium;

import java.util.List;
import com.nvidia.rtxremix.minecraft.terrain.capture.SectionMeshData;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockQuadEmitter;
import com.nvidia.rtxremix.minecraft.terrain.capture.BlockDataQueue;
import com.nvidia.rtxremix.minecraft.terrain.capture.SectionBlockData;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.block.BlockState;
import net.minecraft.world.BlockRenderView;
import net.minecraft.client.texture.Sprite;

/** Worker-local adapter for finished Sodium quads, including renderer extensions. */
public final class SodiumSectionCapture {
    private static final ThreadLocal<SodiumSectionCapture> LOCAL =
            ThreadLocal.withInitial(SodiumSectionCapture::new);
    private final float[] positions = new float[12];
    private final float[] uvs = new float[8];
    private final int[] colors = new int[4];
    private final BlockPos.Mutable blockPos = new BlockPos.Mutable();
    private ChunkSectionPos section;
    private BlockDataQueue.CaptureToken token;
    private boolean active;
    public static SodiumSectionCapture current() {
        return LOCAL.get();
    }

    public void begin(RenderSection render) {
        active = RtxRemixMod.isRtxEnabled();
        if (!active)
            return;
        section = ChunkSectionPos.from(render.getChunkX(), render.getChunkY(), render.getChunkZ());
        token = BlockDataQueue.beginSectionCapture(section);
        var mod = RtxRemixMod.getInstance();
        var client = MinecraftClient.getInstance();
        BlockQuadEmitter.threadLocal().beginSection(section,
                mod == null || mod.getRenderer() == null ? null : mod.getRenderer().getTextureManager(),
                client.getBlockRenderManager());
    }

    public void block(BlockRenderView world, BlockState state, int x, int y, int z) {
        if (!active)
            return;
        var emitter = BlockQuadEmitter.threadLocal();
        emitter.flushRenderedBlock();
        blockPos.set(x, y, z);
        emitter.processBlock(state, world, blockPos, state.isOpaqueFullCube(world, blockPos));
    }

    public void quad(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        if (!active)
            return;
        float u = 0, v = 0;
        for (int i = 0; i < 4; i++) {
            var vertex = vertices[i];
            positions[i * 3] = vertex.x;
            positions[i * 3 + 1] = vertex.y;
            positions[i * 3 + 2] = vertex.z;
            uvs[i * 2] = vertex.u;
            uvs[i * 2 + 1] = vertex.v;
            // Sodium keeps AO separate from albedo; Remix supplies its own lighting.
            int c = vertex.color;
            colors[i] = (c & 0xff00ff00) | ((c & 255) << 16) | ((c >>> 16) & 255);
            u += vertex.u;
            v += vertex.v;
        }
        Sprite sprite = SpriteFinderCache.forBlockAtlas().find(u * .25f, v * .25f);
        float ax = positions[6] - positions[0], ay = positions[7] - positions[1],
              az = positions[8] - positions[2];
        float bx = positions[9] - positions[3], by = positions[10] - positions[4],
              bz = positions[11] - positions[5];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0)
            return;
        String layer = material.isTranslucent()           ? "translucent"
                : material.pass.supportsFragmentDiscard() ? (material.mipped ? "cutout_mipped" : "cutout")
                                                          : "solid";
        var spriteId = sprite == null ? null : sprite.getContents().getId();
        if (com.nvidia.rtxremix.minecraft.texture.BlockMaterialCatalog.get().isGlassSprite(spriteId))
            layer = "glass";
        BlockQuadEmitter.threadLocal().stageRenderedQuad(
                positions, uvs, colors, nx / length, ny / length, nz / length, sprite, layer);
    }

    public void finish(Object output) {
        if (!active)
            return;
        SectionBlockData data = BlockQuadEmitter.threadLocal().finishSection(section);
        ((SodiumCapturedOutput) output).rtxremix$setCapture(data, token);
    }

    public static SectionBlockData emptySection(int x, int y, int z) {
        return new SectionBlockData(ChunkSectionPos.from(x, y, z), new SectionMeshData(List.of()), List.of(),
                -1L, -1L, -1L, -1L, -1L, -1L, new long[64]);
    }

    public void close() {
        if (active && BlockQuadEmitter.threadLocal().isActive())
            BlockQuadEmitter.threadLocal().abortSection();
        active = false;
        section = null;
        token = null;
    }
}

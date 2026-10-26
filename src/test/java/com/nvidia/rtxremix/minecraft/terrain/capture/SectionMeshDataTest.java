/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

import com.nvidia.rtxremix.minecraft.terrain.ChunkManager;
import com.nvidia.rtxremix.HardcodedVertex;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SectionMeshDataTest {

    @Test
    void cachesHashesIndependentlyByCategory() {
        SectionMeshData original = meshData(
                surface(ChunkManager.CAT_SOLID, 1.0f, 4, 6),
                surface(ChunkManager.CAT_OVERLAY, 2.0f, 4, 6));
        SectionMeshData changedOverlay = meshData(
                surface(ChunkManager.CAT_SOLID, 1.0f, 4, 6),
                surface(ChunkManager.CAT_OVERLAY, 3.0f, 4, 6));

        assertEquals(original.categoryContentHash(ChunkManager.CAT_SOLID),
                changedOverlay.categoryContentHash(ChunkManager.CAT_SOLID));
        assertNotEquals(original.categoryContentHash(ChunkManager.CAT_OVERLAY),
                changedOverlay.categoryContentHash(ChunkManager.CAT_OVERLAY));
        assertNotEquals(original.contentHash(), changedOverlay.contentHash());
        assertEquals(0, original.categoryContentHash(ChunkManager.CAT_CUTOUT));
    }

    @Test
    void categoryHashesAreComputedAtConstruction() {
        SectionMeshData data = meshData(surface(ChunkManager.CAT_SOLID, 1.0f, 4, 6));
        int hash = data.categoryContentHash(ChunkManager.CAT_SOLID);

        data.surfaces().get(0).vertexBuffer().putFloat(0, 99.0f);

        assertEquals(hash, data.categoryContentHash(ChunkManager.CAT_SOLID));
    }

    @Test
    void estimatesMeshPayloadIndependentlyByCategory() {
        SectionMeshData data = meshData(
                surface(ChunkManager.CAT_SOLID, 1.0f, 4, 6),
                surface(ChunkManager.CAT_SOLID, 2.0f, 8, 12),
                surface(ChunkManager.CAT_TRANSLUCENT, 3.0f, 4, 6));

        long oneQuadBytes = 4L * HardcodedVertex.STRIDE + 6L * Integer.BYTES;
        assertEquals(3L * oneQuadBytes, data.categoryEstimatedMeshBytes(ChunkManager.CAT_SOLID));
        assertEquals(oneQuadBytes, data.categoryEstimatedMeshBytes(ChunkManager.CAT_TRANSLUCENT));
        assertEquals(0L, data.categoryEstimatedMeshBytes(ChunkManager.CAT_CUTOUT));
    }

    @Test
    void categoryAccessRejectsOutOfRangeValues() {
        SectionMeshData data = meshData();

        assertThrows(IllegalArgumentException.class, () -> data.categoryContentHash(-1));
        assertThrows(IllegalArgumentException.class,
                () -> data.categoryEstimatedMeshBytes(ChunkManager.CAT_COUNT));
    }

    @Test
    void lastReleaseRetiresTheBuffersButKeepsTheHashesReadable() {
        SectionMeshData data = meshData(surface(ChunkManager.CAT_SOLID, 1.0f, 4, 6));
        int hash = data.categoryContentHash(ChunkManager.CAT_SOLID);

        data.retain();
        data.release();
        assertEquals(1, data.surfaces().size(), "a reader's reference keeps the buffers");

        data.release();
        assertThrows(IllegalStateException.class, data::surfaces);
        assertThrows(IllegalStateException.class, data::retain);
        assertEquals(hash, data.categoryContentHash(ChunkManager.CAT_SOLID),
                "a replaced entry is still diffed by its cached hashes");
    }

    @Test
    void releasingMoreOftenThanRetainedFailsInsteadOfPoolingTwice() {
        SectionMeshData data = meshData(surface(ChunkManager.CAT_SOLID, 1.0f, 4, 6));
        data.release();

        assertThrows(IllegalStateException.class, data::release);
        assertThrows(IllegalStateException.class, data::surfaces);
    }

    @Test
    void differentOpeningsWithIdenticalOldHashesStillReplaceTerrain() {
        var before = opening(false);
        var after = opening(true);
        assertEquals(before.contentHash(), after.contentHash(), "regression fixture must reproduce the old collision");
        assertNotEquals(before.categoryFingerprint(ChunkManager.CAT_SOLID),
                after.categoryFingerprint(ChunkManager.CAT_SOLID));
        var terrain = new com.nvidia.rtxremix.minecraft.terrain.TerrainState(128, 0, 0);
        try {
            terrain.addSection(0, 0, 0, before.contentHash(), before);
            var updated = terrain.addSection(0, 0, 0, after.contentHash(), after);
            org.junit.jupiter.api.Assertions.assertNotNull(updated,
                    "a changed opening must not be discarded as unchanged geometry");
            org.junit.jupiter.api.Assertions.assertSame(after, updated.data);
        } finally {
            terrain.clearForRetirement();
        }
    }

    private static SectionMeshData opening(boolean changed) {
        var vertices = ByteBuffer.allocate(8 * HardcodedVertex.STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        int[][] ranges = changed ? new int[][] {{1, 2}, {3, 4}} : new int[][] {{1, 3}, {2, 4}};
        for (int[] range : ranges) {
            for (int i = 0; i < 4; i++) {
                float x = i == 0 || i == 3 ? range[0] : range[1];
                float y = i < 2 ? 0 : 1;
                vertices.putFloat(x).putFloat(y).putFloat(0);
                vertices.putFloat(0).putFloat(0).putFloat(1);
                vertices.putFloat(0).putFloat(0).putInt(-1);
                for (int pad = 0; pad < 7; pad++) vertices.putInt(0);
            }
        }
        vertices.flip();
        return meshData(new SectionMeshData.Surface(ChunkManager.CAT_SOLID, "solid", vertices, 8, 12, true));
    }

    private static SectionMeshData meshData(SectionMeshData.Surface... surfaces) {
        return new SectionMeshData(List.of(surfaces));
    }

    private static SectionMeshData.Surface surface(
            int category, float firstPosition, int vertexCount, int indexCount) {
        ByteBuffer vertices = ByteBuffer.allocate(vertexCount * HardcodedVertex.STRIDE)
                .order(ByteOrder.LITTLE_ENDIAN);
        vertices.putFloat(0, firstPosition);
        return new SectionMeshData.Surface(
                category, "solid", vertices, vertexCount, indexCount, true);
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.dh;

import com.nvidia.rtxremix.minecraft.asset.RemixMeshSurface;
import java.nio.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DistantLodMeshTest {
    private static ByteBuffer quad(int x, int z, int size) {
        var buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        for (int[] offset : new int[][] {{0, 0}, {0, 1}, {1, 1}, {1, 0}}) {
            buffer.putShort((short) (x + offset[0] * size))
                    .putShort((short) 512)
                    .putShort((short) (z + offset[1] * size));
            buffer.putShort((short) 0).put((byte) 0x33).put((byte) 0x66).put((byte) 0x99).put((byte) 0xff);
            buffer.putInt(0);
        }
        return buffer.flip();
    }
    private static ByteBuffer buffer(RemixMeshSurface surface, String name) throws Exception {
        var field = RemixMeshSurface.class.getDeclaredField(name);
        field.setAccessible(true);
        var value = (ByteBuffer) field.get(surface);
        return value.duplicate().order(value.order());
    }
    private static DistantLodData.Page page(ByteBuffer source) {
        return new DistantLodData(0, -64, 0, 64, List.of(source), List.of()).pages.getFirst();
    }
    @Test
    void copiesPooledDhMemoryWithoutChangingItsPosition() {
        var source = quad(0, 0, 32);
        source.mark();
        var page = page(source);
        source.put(0, (byte) 99);
        assertEquals(0, page.vertices()[0]);
        assertEquals(0, source.position());
        source.reset();
        assertEquals(ByteOrder.LITTLE_ENDIAN, page.order());
    }
    @Test
    void decodesUnsignedCoordinatesColourNormalsAndQuadIndices() throws Exception {
        var surface = new DistantLodMesh().decode(page(quad(40000, 50000, 32)), null, null);
        try {
            ByteBuffer vertices = buffer(surface, "vertexData"), indices = buffer(surface, "indexData");
            assertEquals(40000, vertices.getFloat(0));
            assertEquals(512, vertices.getFloat(4));
            assertEquals(50000, vertices.getFloat(8));
            assertEquals(1, vertices.getFloat(16));
            assertEquals(0xff336699, vertices.getInt(32));
            assertEquals(6, indices.remaining() / 4);
            assertEquals(0, indices.getInt());
            assertEquals(1, indices.getInt());
            assertEquals(2, indices.getInt());
        } finally {
            surface.disposeData();
        }
    }
    @Test
    void clipsCrossingQuadsInsteadOfCoveringTheDetailedWorld() throws Exception {
        var cut = new DistantLodMesh.Cut(8, 24, 8, 24);
        var surface = new DistantLodMesh().decode(page(quad(0, 0, 32)), cut, null);
        try {
            ByteBuffer vertices = buffer(surface, "vertexData"), indices = buffer(surface, "indexData");
            double area = 0;
            while (indices.hasRemaining()) {
                int a = indices.getInt() * 64, b = indices.getInt() * 64, c = indices.getInt() * 64;
                float ax = vertices.getFloat(a), az = vertices.getFloat(a + 8), bx = vertices.getFloat(b),
                      bz = vertices.getFloat(b + 8), cx = vertices.getFloat(c), cz = vertices.getFloat(c + 8);
                area += Math.abs((bx - ax) * (cz - az) - (bz - az) * (cx - ax)) * .5;
                float centerX = (ax + bx + cx) / 3, centerZ = (az + bz + cz) / 3;
                assertFalse(centerX > 8 && centerX < 24 && centerZ > 8 && centerZ < 24);
            }
            assertEquals(768, area, .001);
        } finally {
            surface.disposeData();
        }
    }
    @Test
    void entirelyNearQuadsProduceNoLodSurface() {
        assertNull(new DistantLodMesh().decode(
                page(quad(8, 8, 16)), new DistantLodMesh.Cut(0, 32, 0, 32), null));
    }
    @Test
    void rejectsUnknownLayoutBeforeItCanReadAnotherPooledAllocation() {
        assertThrows(IllegalArgumentException.class, () -> page(ByteBuffer.allocate(63)));
    }
}

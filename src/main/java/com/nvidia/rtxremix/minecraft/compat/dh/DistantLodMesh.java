/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.dh;

import com.nvidia.rtxremix.HardcodedVertex;
import com.nvidia.rtxremix.minecraft.asset.RemixMaterial;
import com.nvidia.rtxremix.minecraft.asset.RemixMeshSurface;
import com.nvidia.rtxremix.minecraft.util.DirectBufferPool;
import java.nio.ByteBuffer;

/** Decodes DH's unsigned-short/RGBA vertices and cuts out the detailed terrain footprint. No GL readback. */
public final class DistantLodMesh {
    public record Cut(float minX, float maxX, float minZ, float maxZ) {}
    private static final int ATTRIBUTES = 7;
    private final float[][] polygons = {new float[56], new float[56], new float[56]};
    private ByteBuffer vertices, indices;
    private int vertexCount, indexCount;
    private float nx, ny, nz;

    public RemixMeshSurface decode(DistantLodData.Page page, Cut cut, RemixMaterial material) {
        int quads = page.vertices().length / 64;
        // A rectangle minus a rectangle has at most four convex pieces.
        vertices = DirectBufferPool.acquire(quads * (cut == null ? 4 : 24) * HardcodedVertex.STRIDE);
        indices = DirectBufferPool.acquire(quads * (cut == null ? 6 : 48) * Integer.BYTES);
        vertexCount = indexCount = 0;
        try {
            ByteBuffer input = ByteBuffer.wrap(page.vertices()).order(page.order());
            while (input.hasRemaining()) {
                float[] original = polygons[0];
                for (int v = 0; v < 4; v++) {
                    int base = input.position(), a = v * ATTRIBUTES;
                    original[a] = Short.toUnsignedInt(input.getShort());
                    original[a + 1] = Short.toUnsignedInt(input.getShort());
                    original[a + 2] = Short.toUnsignedInt(input.getShort());
                    for (int c = 0; c < 4; c++)
                        original[a + 3 + c] = Byte.toUnsignedInt(input.get(base + 8 + c));
                    input.position(base + 16);
                }
                float ax = original[7] - original[0], ay = original[8] - original[1],
                      az = original[9] - original[2];
                float bx = original[14] - original[0], by = original[15] - original[1],
                      bz = original[16] - original[2];
                nx = ay * bz - az * by;
                ny = az * bx - ax * bz;
                nz = ax * by - ay * bx;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length == 0)
                    continue;
                nx /= length;
                ny /= length;
                nz /= length;
                if (cut == null) {
                    emit(original, 4);
                    continue;
                }
                int count = 4;
                float[] remaining = original, inside = polygons[1], outside = polygons[2];
                for (int plane = 0; plane < 4 && count > 0; plane++) {
                    int axis = plane < 2 ? 0 : 2;
                    float boundary = switch (plane) {case 0->cut.minX();case 1->cut.maxX();case 2->cut.minZ();default->cut.maxZ();};
                    boolean keepGreater=(plane&1)==0;
                    int outsideCount=clip(remaining,count,outside,axis,boundary,!keepGreater,false);
                    emit(outside,outsideCount);
                    count=clip(remaining,count,inside,axis,boundary,keepGreater,true);
                    float[] swap=remaining;remaining=inside;inside=swap;
                }
            }
            if(indexCount==0){DirectBufferPool.release(vertices);DirectBufferPool.release(indices);return null;}
            vertices.flip();indices.flip();
            return new RemixMeshSurface(material).setVertexData(vertices,vertexCount).setIndexData(indices,indexCount);
        } catch(RuntimeException|Error e){
            DirectBufferPool.release(vertices);DirectBufferPool.release(indices);throw e;
        } finally {vertices=null;indices=null;}
    }
    private static int clip(float[] in,int count,float[] out,int axis,float boundary,boolean greater,boolean inclusive){
        int n=0;
        for(int i=0;i<count;i++){
            int a=i*ATTRIBUTES,b=((i+1)%count)*ATTRIBUTES;
            float da=(in[a+axis]-boundary)*(greater?1:-1),db=(in[b+axis]-boundary)*(greater?1:-1);
                            boolean ka = inclusive ? da >= 0 : da > 0, kb = inclusive ? db >= 0 : db > 0;
                            if (ka) {
                                System.arraycopy(in, a, out, n * ATTRIBUTES, ATTRIBUTES);
                                n++;
                            }
                            if (ka != kb) {
                                float t = da / (da - db);
                                for (int c = 0; c < ATTRIBUTES; c++)
                                    out[n * ATTRIBUTES + c] = in[a + c] + t * (in[b + c] - in[a + c]);
                                n++;
                            }
                    }
                    return n;
                }
                private void emit(float[] polygon, int count) {
                    if (count < 3)
                        return;
                    // Plane-coincident pieces have no area and must not cover the near terrain boundary
                    // twice.
                    float area = 0;
                    for (int i = 1; i + 1 < count; i++) {
                        int a = i * ATTRIBUTES, b = (i + 1) * ATTRIBUTES;
                        float ax = polygon[a] - polygon[0], ay = polygon[a + 1] - polygon[1],
                              az = polygon[a + 2] - polygon[2];
                        float bx = polygon[b] - polygon[0], by = polygon[b + 1] - polygon[1],
                              bz = polygon[b + 2] - polygon[2];
                        area += Math.abs((ay * bz - az * by) * nx + (az * bx - ax * bz) * ny
                                + (ax * by - ay * bx) * nz);
                    }
                    if (area < .00001f)
                        return;
                    int start = vertexCount;
                    for (int i = 0; i < count; i++) {
                        int p = i * ATTRIBUTES;
                        int color = Math.round(polygon[p + 6]) << 24 | Math.round(polygon[p + 3]) << 16
                                | Math.round(polygon[p + 4]) << 8 | Math.round(polygon[p + 5]);
                        RemixMeshSurface.putVertex(vertices, polygon[p], polygon[p + 1], polygon[p + 2], nx,
                                ny, nz, 0, 0, color, 0, 0);
                        vertexCount++;
                    }
                    for (int i = 1; i + 1 < count; i++) {
                        indices.putInt(start).putInt(start + i).putInt(start + i + 1);
                        indexCount += 3;
                    }
                }
            }

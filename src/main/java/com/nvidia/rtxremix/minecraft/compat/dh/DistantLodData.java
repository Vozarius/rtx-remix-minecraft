/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.dh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Owned copies: DH returns its pooled source buffers immediately after asynchronous GL upload. */
public final class DistantLodData {
    public static final int PAGE_BYTES = 1024 * 64;
    public record Page(byte[] vertices, ByteOrder order, boolean translucent) {}
    public record SectionKey(int x, int y, int z, int width) {}
    public final SectionKey sectionKey;
    public final int x, y, z, width;
    public final List<Page> pages;
    public volatile boolean closed;
    public DistantLodData(
            int x, int y, int z, int width, List<ByteBuffer> opaque, List<ByteBuffer> translucent) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        sectionKey = new SectionKey(x, y, z, width);
        var result = new ArrayList<Page>();
        copy(opaque, false, result);
        copy(translucent, true, result);
        pages = List.copyOf(result);
    }
    private static void copy(List<ByteBuffer> source, boolean translucent, List<Page> result) {
        for (ByteBuffer input : source) {
            if (input.remaining() % 64 != 0)
                throw new IllegalArgumentException("DH 3.3.3 quad layout must be 64-byte aligned");
            var view = input.duplicate();
            while (view.hasRemaining()) {
                byte[] bytes = new byte[Math.min(PAGE_BYTES, view.remaining())];
                view.get(bytes);
                result.add(new Page(bytes, input.order(), translucent));
            }
        }
    }
}

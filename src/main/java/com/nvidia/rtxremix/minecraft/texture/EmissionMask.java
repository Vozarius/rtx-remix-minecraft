/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

import net.minecraft.client.texture.NativeImage;

/** Resource-pack _e.png sidecars: white emits albedo, black is dark, grey scales emission. */
public final class EmissionMask {
    private EmissionMask() {}
    public static byte[] apply(byte[] albedo, int w, int h, NativeImage mask) {
        byte[] out = new byte[albedo.length];
        blit(out, w, albedo, 0, 0, w, h, mask);
        return out;
    }
    public static void blit(
            byte[] out, int stride, byte[] albedo, int px, int py, int w, int h, NativeImage mask) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int pixel = mask.getColor(
                        x * mask.getWidth() / w, y * Math.min(mask.getHeight(), h * mask.getWidth() / w) / h);
                float gain = strength(pixel);
                int i = ((py + y) * stride + px + x) * 4;
                for (int c = 0; c < 3; c++) out[i + c] = (byte) Math.round((albedo[i + c] & 255) * gain);
                out[i + 3] = gain > 0 ? albedo[i + 3] : 0;
            }
    }
    public static float strength(int abgr) {
        return Math.max(abgr & 255, Math.max((abgr >>> 8) & 255, (abgr >>> 16) & 255)) * ((abgr >>> 24) & 255)
                / 65025f;
    }
    public static byte[] alphaTable(NativeImage mask) {
        return alphaTable(mask, mask.getWidth(), mask.getHeight());
    }
    public static byte[] alphaTable(NativeImage mask, int w, int h) {
        byte[] out = new byte[w * h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int sx = x * mask.getWidth() / w;
                int sy = mask.getHeight() <= mask.getWidth() ? (y % w) * mask.getHeight() / w
                                                             : y * mask.getHeight() / h;
                float strength = strength(mask.getColor(sx, sy));
                out[y * w + x] = (byte) (strength == 0 ? 255 : Math.round(strength * 254));
            }
        return out;
    }
}

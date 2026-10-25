/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.texture;

/** Registry namespace determines the base animation, independently of a shared source sprite. */
public final class FluidTextureStyle {
    private FluidTextureStyle() {}
    public static boolean lava(String fluidId) {
        String namespace = fluidId.substring(0, fluidId.indexOf(':'));
        return namespace.equals("minecraft")
                && (fluidId.equals("minecraft:lava") || fluidId.equals("minecraft:flowing_lava"))
                || namespace.equals("aeronautics") || namespace.equals("create_aeronautics")
                || namespace.equals("create_enchantment_industry")
                || namespace.equals("enchantment_industry");
    }
    /** Recolours a base animation by relative luminance, retaining the original mean colour and coverage. */
    public static byte[] recolor(byte[] base, TextureAverage target) {
        double mean = 0, peak = 0;
        for (int i = 0; i < base.length; i += 4) {
            double luminance = luma(base, i);
            mean += luminance;
            peak = Math.max(peak, luminance);
        }
        mean /= base.length / 4;
        float peakVariation = (float) (mean == 0 ? 1 : peak / mean);
        float redScale = contrast(target.red(), peakVariation);
        float greenScale = contrast(target.green(), peakVariation);
        float blueScale = contrast(target.blue(), peakVariation);
        byte[] result = new byte[base.length];
        for (int i = 0; i < base.length; i += 4) {
            float variation = (float) (mean == 0 ? 0 : luma(base, i) / mean - 1);
            result[i] = encode(target.red() + redScale * variation);
            result[i + 1] = encode(target.green() + greenScale * variation);
            result[i + 2] = encode(target.blue() + blueScale * variation);
            result[i + 3] = encode(target.alpha());
        }
        return result;
    }
    // Reduce contrast near saturation rather than clipping highlights and shifting the mean colour.
    private static float contrast(float channel, float peakVariation) {
        return peakVariation <= 1 ? channel : Math.min(channel, (1 - channel) / (peakVariation - 1));
    }
    private static byte encode(float value) {
        return (byte) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
    private static double luma(byte[] rgba, int i) {
        return (rgba[i] & 255) * .2126 + (rgba[i + 1] & 255) * .7152 + (rgba[i + 2] & 255) * .0722;
    }
}

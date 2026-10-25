/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.create;

import com.nvidia.rtxremix.minecraft.entity.RemixCapturingConsumer;
import net.createmod.catnip.render.TemplateMesh;
import net.createmod.catnip.render.SuperByteBuffer;
import org.joml.Matrix4f;

/** Keeps animated Catnip buffers in model space so rotations reuse a single native mesh. */
public final class CreateBufferCapture {
    private CreateBufferCapture() {}

    public static void emit(TemplateMesh template, RemixCapturingConsumer consumer, Matrix4f pose, float r,
            float g, float b, float a, SuperByteBuffer.SpriteShiftFunc shift,
            SuperByteBuffer.ShiftOutput shifted) {
        if (template.isEmpty())
            return;
        int bone = consumer.beginCuboid(pose);
        for (int i = 0, count = template.vertexCount(); i < count; i++) {
            int abgr = template.color(i);
            int color = (int) ((abgr >>> 24 & 255) * a) << 24 | (int) ((abgr & 255) * r) << 16
                    | (int) ((abgr >>> 8 & 255) * g) << 8 | (int) ((abgr >>> 16 & 255) * b);
            float u = template.u(i), v = template.v(i);
            if (shift != null) {
                shift.shift(u, v, shifted);
                u = shifted.u;
                v = shifted.v;
            }
            int normal = template.normal(i);
            consumer.acceptModelSpaceVertex(template.x(i), template.y(i), template.z(i), color, u, v,
                    (byte) normal / 127f, (byte) (normal >>> 8) / 127f, (byte) (normal >>> 16) / 127f, bone);
        }
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.create;

import com.nvidia.rtxremix.minecraft.entity.EntityRenderContext;
import com.nvidia.rtxremix.minecraft.entity.RemixCapturingConsumer;
import net.createmod.catnip.render.TemplateMesh;
import net.createmod.catnip.render.SuperByteBuffer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class CreateBufferCaptureTest {
    @AfterEach
    void reset() {
        EntityRenderContext.get().reset();
    }

    private static TemplateMesh quad() {
        int[] data = new int[4 * TemplateMesh.INT_STRIDE];
        for (int i = 0; i < 4; i++) {
            int base = i * TemplateMesh.INT_STRIDE;
            data[base + TemplateMesh.X_OFFSET] = Float.floatToRawIntBits(i < 2 ? 0 : 1);
            data[base + TemplateMesh.Y_OFFSET] = Float.floatToRawIntBits(i == 0 || i == 3 ? 0 : 1);
            data[base + TemplateMesh.COLOR_OFFSET] = 0x80604020;
            data[base + TemplateMesh.U_OFFSET] = Float.floatToRawIntBits(.25f);
            data[base + TemplateMesh.V_OFFSET] = Float.floatToRawIntBits(.5f);
            data[base + TemplateMesh.NORMAL_OFFSET] = 127 << 16;
        }
        return new TemplateMesh(data);
    }

    @Test
    void rotatingMechanismReusesIdenticalGeometryAcrossOneHundredTwentyFrames() {
        var template = quad();
        var consumer = new RemixCapturingConsumer(null, true);
        var hashes = new HashSet<Long>();
        for (int frame = 0; frame < 120; frame++) {
            var context = EntityRenderContext.get();
            context.begin(true);
            consumer.clear();
            var pose = new Matrix4f().translate(300, 70, -600).rotateY(frame * .03f);
            CreateBufferCapture.emit(template, consumer, pose, 1, 1, 1, 1, null, null);
            consumer.finishOpenVertex();
            assertEquals(1, consumer.getPartCount());
            assertEquals(4, consumer.getPartVertexCount(0));
            assertEquals(0, consumer.getPx()[0]);
            assertEquals(1, consumer.getNz()[0]);
            assertEquals(pose, context.getBoneTransforms().getFirst());
            hashes.add(consumer.getPartHash(0));
            context.reset();
        }
        assertEquals(1, hashes.size(), "Animated transforms must not build a new native mesh each frame");
    }

    @Test
    void retainsOriginalAlphaTintNormalsAndSpriteShift() {
        var consumer = new RemixCapturingConsumer(null, true);
        EntityRenderContext.get().begin(true);
        CreateBufferCapture.emit(quad(), consumer, new Matrix4f(), .5f, 1, 1, .5f,
                (u, v, output) -> output.accept(u + .125f, v + .25f), new SuperByteBuffer.ShiftOutput());
        consumer.finishOpenVertex();
        assertEquals(0x40104060, consumer.getColors()[0]);
        assertEquals(.375f, consumer.getTu()[0]);
        assertEquals(.75f, consumer.getTv()[0]);
        assertEquals(1, consumer.getNz()[0]);
    }

    @Test
    void emptyBufferDoesNotAllocateABoneOrPart() {
        var consumer = new RemixCapturingConsumer(null, true);
        var context = EntityRenderContext.get();
        context.begin(true);
        CreateBufferCapture.emit(new TemplateMesh(0), consumer, new Matrix4f(), 1, 1, 1, 1, null, null);
        consumer.finishOpenVertex();
        assertEquals(0, context.getBoneCount());
        assertEquals(0, consumer.getPartCount());
    }
}

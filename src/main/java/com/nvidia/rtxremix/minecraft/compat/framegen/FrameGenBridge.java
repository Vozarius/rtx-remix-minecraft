/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.framegen;

import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.gl.RemixDepthCopy;
import dev.screamix.framegen.client.FrameGenRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;

/** FrameGen history must contain the composited traced image and its matching depth. */
public final class FrameGenBridge {
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static Camera pendingCamera;
    private static boolean committing;
    private static RemixDepthCopy depthCopy;

    private FrameGenBridge() {}

    public static boolean defer(Camera camera, Matrix4f view, Matrix4f projection) {
        if (committing) return false;
        if (!RtxRemixMod.isRtxEnabled() || !FrameGenRuntime.isEnabled()) { pendingCamera = null; return false; }
        pendingCamera = camera;
        VIEW.set(view);
        PROJECTION.set(projection);
        return true;
    }

    public static void capture(int depthTexture) {
        Camera camera = pendingCamera;
        pendingCamera = null;
        if (camera == null || depthTexture == 0) return;
        var client = MinecraftClient.getInstance();
        client.getFramebuffer().beginWrite(true);
        if (depthCopy == null) depthCopy = new RemixDepthCopy();
        depthCopy.seed(depthTexture, client.getWindow().getFramebufferWidth(), client.getWindow().getFramebufferHeight());
        committing = true;
        try { FrameGenRuntime.captureFrame(camera, VIEW, PROJECTION); }
        finally { committing = false; }
    }

    public static void close() {
        pendingCamera = null;
        if (depthCopy != null) { depthCopy.destroy(); depthCopy = null; }
    }
}

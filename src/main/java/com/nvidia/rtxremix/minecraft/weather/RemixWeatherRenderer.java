/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.weather;

import com.nvidia.rtxremix.minecraft.gl.RemixDepthCopy;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws rain and snow with GL directly onto the composited Remix colour image,
 * depth-testing against the interop depth texture so weather is occluded by
 * path-traced geometry.
 *
 * <p>Render thread only, and after Remix has presented its image into GL: the
 * renderer owns its GL buffers and reads the interop textures the frame just
 * produced.
 */
public final class RemixWeatherRenderer {
    private static final float NEAR_PLANE = 0.05f;
    private static final float FAR_PLANE = 1000.0f;
    private static final Identifier RAIN_TEXTURE =
            Identifier.ofVanilla("textures/environment/rain.png");
    private static final Identifier RAIN_SHADER =
            Identifier.of(RtxRemixCommonMod.MOD_ID, "rain_refraction");

    private static ShaderProgram rainShader;

    private final List<DeferredDraw> draws = new ArrayList<>(2);
    private Matrix4f projection;
    private Matrix4f modelView;
    private LightmapTextureManager lightmap;
    private RemixDepthCopy depthSeeder;
    private int capturedQuadCount;
    private int lastDrawCount;
    private int lastQuadCount;

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderProgram(event.getResourceProvider(), RAIN_SHADER,
                    VertexFormats.POSITION_TEXTURE_COLOR_LIGHT), shader -> rainShader = shader);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Cannot load the Remix rain shader", e);
        }
    }

    public void beginCaptureFrame(LightmapTextureManager lightmapTextureManager) {
        closePendingDraws();
        lightmap = lightmapTextureManager;
        capturedQuadCount = 0;
        lastDrawCount = 0;
        lastQuadCount = 0;
    }

    public void updateCamera(Camera camera, float fovYDegrees, float aspect) {
        Quaternionf cameraRotation = camera.getRotation().conjugate(new Quaternionf());
        modelView = new Matrix4f().rotation(cameraRotation);
        projection = new Matrix4f().perspective(
                (float) Math.toRadians(fovYDegrees),
                aspect,
                NEAR_PLANE,
                FAR_PLANE);
    }

    public void capture(BuiltBuffer buffer, Identifier texture) {
        if (texture == null) {
            buffer.close();
            throw new IllegalStateException("Weather draw did not bind a texture");
        }
        capturedQuadCount += buffer.getDrawParameters().vertexCount() / 4;
        draws.add(new DeferredDraw(buffer, texture, texture.equals(RAIN_TEXTURE)));
    }

    public void render(int finalColorTexture, int depthTexture) {
        if (draws.isEmpty()) {
            return;
        }
        if (depthTexture == 0) {
            closePendingDraws();
            throw new IllegalStateException("Remix depth texture is unavailable");
        }
        if (finalColorTexture == 0) {
            closePendingDraws();
            throw new IllegalStateException("Remix final-color texture is unavailable");
        }
        if (projection == null || modelView == null || lightmap == null) {
            closePendingDraws();
            throw new IllegalStateException("Weather camera state was not initialized");
        }

        MinecraftClient client = MinecraftClient.getInstance();
        client.getFramebuffer().beginWrite(true);
        try {
            if (depthSeeder == null) {
                depthSeeder = new RemixDepthCopy();
            }
            depthSeeder.seed(
                    depthTexture,
                    client.getWindow().getFramebufferWidth(),
                    client.getWindow().getFramebufferHeight());
            lastDrawCount = draws.size();
            lastQuadCount = capturedQuadCount;
            renderDeferredDraws(finalColorTexture);
        } catch (RuntimeException exception) {
            closePendingDraws();
            throw exception;
        }
    }

    private void renderDeferredDraws(int finalColorTexture) {
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] scissorBox = readScissorBox();
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int blendSourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDestinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDestinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        boolean[] colorMask = readColorMask();
        float[] shaderColor = RenderSystem.getShaderColor().clone();

        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);
        modelViewStack.pushMatrix();
        modelViewStack.set(modelView);
        RenderSystem.applyModelViewMatrix();

        try {
            lightmap.enable();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);
            GL14.glBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            renderRain(finalColorTexture);
            renderSnow();
        } finally {
            lightmap.disable();
            closePendingDraws();
            restoreCapability(GL11.GL_DEPTH_TEST, depthTest);
            restoreCapability(GL11.GL_BLEND, blend);
            restoreCapability(GL11.GL_CULL_FACE, cull);
            restoreCapability(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            GL11.glDepthFunc(depthFunction);
            GL11.glDepthMask(depthMask);
            GL11.glColorMask(
                    colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL14.glBlendFuncSeparate(
                    blendSourceRgb,
                    blendDestinationRgb,
                    blendSourceAlpha,
                    blendDestinationAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_Z);
            RenderSystem.setShaderColor(
                    shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        }
    }

    private void renderRain(int finalColorTexture) {
        if (rainShader == null && hasRainDraw()) {
            throw new IllegalStateException("Rain refraction shader was not loaded");
        }
        RenderSystem.setShaderTexture(1, finalColorTexture);
        renderDraws(true, rainShader);
    }

    private boolean hasRainDraw() {
        for (DeferredDraw draw : draws) {
            if (draw != null && draw.rain()) {
                return true;
            }
        }
        return false;
    }

    private void renderSnow() {
        renderDraws(false, GameRenderer.getParticleProgram());
    }

    private void renderDraws(boolean rain, ShaderProgram shader) {
        if (shader == null) {
            return;
        }
        RenderSystem.setShader(() -> shader);
        for (int i = 0; i < draws.size(); i++) {
            DeferredDraw draw = draws.get(i);
            if (draw == null || draw.rain() != rain) {
                continue;
            }
            RenderSystem.setShaderTexture(0, draw.texture());
            try {
                BufferRenderer.drawWithGlobalProgram(draw.buffer());
            } finally {
                draws.set(i, null);
            }
        }
    }

    private static void restoreCapability(int capability, boolean enabled) {
        if (enabled) GL11.glEnable(capability);
        else GL11.glDisable(capability);
    }

    private static int[] readScissorBox() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, values);
            return new int[]{values.get(0), values.get(1), values.get(2), values.get(3)};
        }
    }

    private static boolean[] readColorMask() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer values = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
            return new boolean[]{
                    values.get(0) != 0,
                    values.get(1) != 0,
                    values.get(2) != 0,
                    values.get(3) != 0
            };
        }
    }

    private void closePendingDraws() {
        for (DeferredDraw draw : draws) {
            if (draw != null) {
                draw.buffer().close();
            }
        }
        draws.clear();
    }

    public int getLastDrawCount() {
        return lastDrawCount;
    }

    public int getLastQuadCount() {
        return lastQuadCount;
    }

    public void cleanup() {
        closePendingDraws();
        if (depthSeeder != null) {
            depthSeeder.destroy();
            depthSeeder = null;
        }
    }

    private record DeferredDraw(BuiltBuffer buffer, Identifier texture, boolean rain) {
    }

}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Copies the traced scene depth into the currently bound OpenGL framebuffer. */
public final class RemixDepthCopy {
    private static final String VERTEX_SHADER = """
            #version 150
            void main() {
                vec2 position = vec2(
                    float((gl_VertexID << 1) & 2),
                    float(gl_VertexID & 2));
                gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150
            uniform sampler2D RemixDepth;
            uniform vec2 FramebufferSize;
            void main() {
                vec2 uv = vec2(
                    gl_FragCoord.x / FramebufferSize.x,
                    1.0 - gl_FragCoord.y / FramebufferSize.y);
                gl_FragDepth = texture(RemixDepth, uv).r;
            }
            """;

    private final int program;
    private final int vertexArray;
    private final int remixDepthLocation;
    private final int framebufferSizeLocation;

    public RemixDepthCopy() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = 0;
        int linkedProgram = 0;
        try {
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
            linkedProgram = GL20.glCreateProgram();
            GL20.glAttachShader(linkedProgram, vertexShader);
            GL20.glAttachShader(linkedProgram, fragmentShader);
            GL20.glLinkProgram(linkedProgram);
            if (GL20.glGetProgrami(linkedProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(linkedProgram);
                GL20.glDeleteProgram(linkedProgram);
                linkedProgram = 0;
                throw new IllegalStateException("Failed to link depth seeding shader: " + log);
            }
        } finally {
            GL20.glDeleteShader(vertexShader);
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
        program = linkedProgram;
        remixDepthLocation = GL20.glGetUniformLocation(program, "RemixDepth");
        framebufferSizeLocation = GL20.glGetUniformLocation(program, "FramebufferSize");
        vertexArray = GL30.glGenVertexArrays();
    }

    public void seed(int depthTexture, int width, int height) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int previousDepthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] previousScissorBox = readScissorBox();
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean[] previousColorMask = readColorMask();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            // OpenGL suppresses depth writes entirely when the depth test is disabled.
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(true);
            GL11.glColorMask(false, false, false, false);
            GL20.glUseProgram(program);
            GL20.glUniform1i(remixDepthLocation, 0);
            GL20.glUniform2f(framebufferSizeLocation, width, height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            GL30.glBindVertexArray(vertexArray);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL30.glBindVertexArray(previousVertexArray);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            GL20.glUseProgram(previousProgram);
            GL11.glColorMask(
                    previousColorMask[0],
                    previousColorMask[1],
                    previousColorMask[2],
                    previousColorMask[3]);
            GL11.glDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunction);
            if (previousDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (previousScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(
                    previousScissorBox[0],
                    previousScissorBox[1],
                    previousScissorBox[2],
                    previousScissorBox[3]);
            if (previousBlend) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);
            if (previousCull) GL11.glEnable(GL11.GL_CULL_FACE);
            else GL11.glDisable(GL11.GL_CULL_FACE);
        }
    }

    public void destroy() {
        GL30.glDeleteVertexArrays(vertexArray);
        GL20.glDeleteProgram(program);
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile depth seeding shader: " + log);
        }
        return shader;
    }
    private final int[] scissorBox = new int[4];
    private final boolean[] colorMask = new boolean[4];

    private int[] readScissorBox() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, values);
            for (int i = 0; i < 4; i++) scissorBox[i] = values.get(i);
            return scissorBox;
        }
    }

    private boolean[] readColorMask() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer values = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, values);
            for (int i = 0; i < 4; i++) colorMask[i] = values.get(i) != 0;
            return colorMask;
        }
    }

}

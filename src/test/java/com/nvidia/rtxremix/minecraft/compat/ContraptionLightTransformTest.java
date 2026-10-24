/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;
import com.nvidia.rtxremix.*;
import com.nvidia.rtxremix.minecraft.compat.create.ContraptionLighting;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContraptionLightTransformTest {
    @Test
    void emitterAndAxesFollowTheSameNestedTransformAsGeometry() {
        Matrix4f root = new Matrix4f().translation(10, 20, 30).rotateY((float) Math.PI / 2).scale(2, 3, 4);
        Transform pose = new Transform();
        ContraptionLighting.pose(root, .25, .5, .75, pose);
        LightInfo source = LightInfo.builder()
                                   .diskExt(LightInfoDiskEXT.builder()
                                                    .xRadius(.2f)
                                                    .yRadius(.3f)
                                                    .direction(0, 0, -1)
                                                    .build())
                                   .build();
        LightInfo result = LightInfo.builder().diskExt(new LightInfoDiskEXT()).build();
        FrameLightTransform.apply(source, result, pose);
        Vector3f expected = root.transformPosition(new Vector3f(.25f, .5f, .75f));
        assertEquals(expected.x, result.diskExt.position.x, 1e-5);
        assertEquals(expected.y, result.diskExt.position.y, 1e-5);
        assertEquals(expected.z, result.diskExt.position.z, 1e-5);
        assertEquals(.4f, result.diskExt.xRadius, 1e-5);
        assertEquals(.9f, result.diskExt.yRadius, 1e-5);
        assertEquals(-1, result.diskExt.direction.x, 1e-5);
        assertEquals(0, result.diskExt.direction.z, 1e-5);
        FrameLightTransform.apply(source, result, pose);
        assertEquals(.4f, result.diskExt.xRadius, 1e-5);
        assertEquals(.2f, source.diskExt.xRadius, 1e-5);
    }
}

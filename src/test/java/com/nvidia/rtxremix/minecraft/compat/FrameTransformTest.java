/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrameTransformTest {
    @Test void retainsFractionalLightSourceCoordinatesBeforePoseApplication() {
        var key = new com.nvidia.rtxremix.minecraft.light.BlockLightKey(new net.minecraft.util.math.BlockPos(30000000, 64, -30000000),
                net.minecraft.util.Identifier.of("test", "lamp"), 0);
        var light = com.nvidia.rtxremix.minecraft.light.LightEntry.sphere(key, 15, null,
                30000000.125, 64.5, -30000000.75, .1f, null, null, .5f);
        assertEquals(30000000.125, light.x());
        assertEquals(-30000000.75, light.z());
    }

    @Test void preservesFractionalOffsetsAtLargePlotCoordinates() {
        Transform out = new Transform();
        FrameTransform.set(new Matrix3d(), new Vector3d(1), new Vector3d(30000000.25, 62.5, -30000000.75),
                new Vector3d(10, 20, 30), 30000000.75, 63, -30000000.25, out);
        assertEquals(10.5f, out.matrix[3]);
        assertEquals(20.5f, out.matrix[7]);
        assertEquals(30.5f, out.matrix[11]);
    }

    @Test void matchesDoublePrecisionPoseForRotationAndNonuniformScale() {
        Matrix3d rotation = new Matrix3d().rotateXYZ(.3, .7, -.2);
        Vector3d scale = new Vector3d(2, .5, 3), pivot = new Vector3d(25000000.125, -42, 27000000.75);
        Vector3d position = new Vector3d(150.25, 80.5, -700.125);
        double x = 25000000, y = 48, z = 27000000;
        Transform out = new Transform();
        FrameTransform.set(rotation, scale, pivot, position, x, y, z, out);
        Vector3d expected = new Vector3d(x + .125, y + .75, z + .5).sub(pivot).mul(scale).mul(rotation).add(position);
        assertEquals(expected.x, out.matrix[0]*.125 + out.matrix[1]*.75 + out.matrix[2]*.5 + out.matrix[3], 1e-4);
        assertEquals(expected.y, out.matrix[4]*.125 + out.matrix[5]*.75 + out.matrix[6]*.5 + out.matrix[7], 1e-4);
        assertEquals(expected.z, out.matrix[8]*.125 + out.matrix[9]*.75 + out.matrix[10]*.5 + out.matrix[11], 1e-4);
    }

    @Test void transformsDirectionalLightsWithoutAccumulatingPoseChanges() {
        LightInfo source = LightInfo.builder().rectExt(LightInfoRectEXT.builder().xSize(2).ySize(3).build()).build();
        LightInfo out = LightInfo.builder().rectExt(LightInfoRectEXT.builder().build()).build();
        Transform pose = Transform.fromTRS(10,20,30,new Quaternionf().rotateZ((float)(java.lang.Math.PI/2)),2);
        FrameLightTransform.apply(source,out,pose);
        assertEquals(10, out.rectExt.position.x);
        assertEquals(4, out.rectExt.xSize, 1e-5);
        assertEquals(6, out.rectExt.ySize, 1e-5);
        assertEquals(1, out.rectExt.xAxis.y, 1e-5);
        FrameLightTransform.apply(source,out,pose);
        assertEquals(4, out.rectExt.xSize, 1e-5);
        assertEquals(2, source.rectExt.xSize);
    }

    @Test void preservesPackedVertexAlphaAndMultipliesTintChannels() {
        assertEquals(0x80201008, QuadColors.tintAbgr(0x80102040, 0xff808080));
        assertEquals(0x80602010, QuadColors.tintAbgr(0x80102060, 0xffffffff));
    }
}

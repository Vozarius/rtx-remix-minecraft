/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.Float3D;
import com.nvidia.rtxremix.LightInfo;
import com.nvidia.rtxremix.Transform;

/** Transforms local light definitions into a reusable native world-space definition. */
public final class FrameLightTransform {
    private FrameLightTransform() {}

    public static void apply(LightInfo source, LightInfo out, Transform pose) {
        if (source.sphereExt != null) {
            var a = source.sphereExt; var b = out.sphereExt;
            position(b.position, pose);
            b.radius = a.radius * maximumScale(pose);
            direction(a.shaping_direction, b.shaping_direction, pose);
        }
        if (source.cylinderExt != null) {
            var a = source.cylinderExt; var b = out.cylinderExt;
            position(b.position, pose);
            b.radius = a.radius * maximumScale(pose);
            b.axisLength = a.axisLength * direction(a.axis, b.axis, pose);
        }
        if (source.rectExt != null) {
            var a = source.rectExt; var b = out.rectExt;
            position(b.position, pose);
            b.xSize = a.xSize * direction(a.xAxis, b.xAxis, pose);
            b.ySize = a.ySize * direction(a.yAxis, b.yAxis, pose);
            direction(a.direction, b.direction, pose);
            direction(a.shaping_direction, b.shaping_direction, pose);
        }
        if (source.diskExt != null) {
            var a = source.diskExt; var b = out.diskExt;
            position(b.position, pose);
            b.xRadius = a.xRadius * direction(a.xAxis, b.xAxis, pose);
            b.yRadius = a.yRadius * direction(a.yAxis, b.yAxis, pose);
            direction(a.direction, b.direction, pose);
            direction(a.shaping_direction, b.shaping_direction, pose);
        }
    }

    private static void position(Float3D out, Transform pose) {
        out.x = pose.matrix[3]; out.y = pose.matrix[7]; out.z = pose.matrix[11];
    }

    private static float direction(Float3D source, Float3D out, Transform pose) {
        float[] m = pose.matrix;
        float x = m[0] * source.x + m[1] * source.y + m[2] * source.z;
        float y = m[4] * source.x + m[5] * source.y + m[6] * source.z;
        float z = m[8] * source.x + m[9] * source.y + m[10] * source.z;
        float length = (float)Math.sqrt(x*x + y*y + z*z);
        if (length == 0) { out.x = out.y = out.z = 0; return 0; }
        out.x = x / length; out.y = y / length; out.z = z / length;
        return length;
    }

    private static float maximumScale(Transform pose) {
        float[] m = pose.matrix;
        return (float)Math.sqrt(Math.max(m[0]*m[0]+m[4]*m[4]+m[8]*m[8],
                Math.max(m[1]*m[1]+m[5]*m[5]+m[9]*m[9], m[2]*m[2]+m[6]*m[6]+m[10]*m[10])));
    }
}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat;

import com.nvidia.rtxremix.Transform;
import org.joml.Matrix3d;
import org.joml.Vector3dc;

/** Cancels large plot coordinates in double precision before storing the native matrix. */
public final class FrameTransform {
    private FrameTransform() {}

    public static void set(Matrix3d rotation, Vector3dc scale, Vector3dc pivot, Vector3dc position,
                           double x, double y, double z, Transform out) {
        double dx = (x - pivot.x()) * scale.x();
        double dy = (y - pivot.y()) * scale.y();
        double dz = (z - pivot.z()) * scale.z();
        float[] m = out.matrix;
        m[0] = (float)(rotation.m00() * scale.x());
        m[1] = (float)(rotation.m10() * scale.y());
        m[2] = (float)(rotation.m20() * scale.z());
        m[3] = (float)(position.x() + rotation.m00() * dx + rotation.m10() * dy + rotation.m20() * dz);
        m[4] = (float)(rotation.m01() * scale.x());
        m[5] = (float)(rotation.m11() * scale.y());
        m[6] = (float)(rotation.m21() * scale.z());
        m[7] = (float)(position.y() + rotation.m01() * dx + rotation.m11() * dy + rotation.m21() * dz);
        m[8] = (float)(rotation.m02() * scale.x());
        m[9] = (float)(rotation.m12() * scale.y());
        m[10] = (float)(rotation.m22() * scale.z());
        m[11] = (float)(position.z() + rotation.m02() * dx + rotation.m12() * dy + rotation.m22() * dz);
    }
}

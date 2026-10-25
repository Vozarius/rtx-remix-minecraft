/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.compat.bits;

import com.nvidia.rtxremix.LightInfo;
import com.nvidia.rtxremix.LightInfoDiskEXT;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Matches Bits 'n' Bobs' nine placements and four ComputerCraft addressing bits. Render-thread only. */
public final class HeadlampPose {
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector3f POINT = new Vector3f(), NORMAL = new Vector3f(), AXIS = new Vector3f();
    private HeadlampPose() {}
    public static boolean lit(long state, int slot) {
        int value = (int) (state >>> (4 + slot * 6) & 63);
        int bit = (slot % 3 == 0 ? 1 : 0) + (slot / 3 == 0 ? 2 : 0);
        return value != 0 && (state & (1L << bit)) != 0;
    }
    public static LightInfo definition() {
        LightInfo info = new LightInfo();
        info.diskExt = new LightInfoDiskEXT();
        return info;
    }
    public static void configure(LightInfo info, long state, int slot, Direction facing) {
        MATRIX.identity().translate(.5f, .5f, .5f);
        if (facing != Direction.UP) {
            MATRIX.rotate(ROTATION.rotationTo(
                    0, 1, 0, facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ()));
            int horizontal = switch (facing) {case SOUTH->0;case WEST->1;case NORTH->2;case EAST->3;default->0;};
            MATRIX.rotateY(horizontal*-(float)Math.PI/2);
        }
        MATRIX.translate(-.5f,-.5f,-.5f);
        MATRIX.transformPosition(.5f+(slot%3-1)*.25f,7f/16+.002f,.5f+(slot/3-1)*.25f,POINT);
        MATRIX.transformDirection(0,1,0,NORMAL);MATRIX.transformDirection(1,0,0,AXIS);
        var disk=info.diskExt;
        disk.position.x=POINT.x;disk.position.y=POINT.y;disk.position.z=POINT.z;
        disk.direction.x=disk.shaping_direction.x=NORMAL.x;
        disk.direction.y=disk.shaping_direction.y=NORMAL.y;
        disk.direction.z=disk.shaping_direction.z=NORMAL.z;
        disk.xAxis.x=AXIS.x;disk.xAxis.y=AXIS.y;disk.xAxis.z=AXIS.z;
        // The other disk axis follows the same right-handed basis as its model.
        disk.yAxis.x=NORMAL.y*AXIS.z-NORMAL.z*AXIS.y;
        disk.yAxis.y=NORMAL.z*AXIS.x-NORMAL.x*AXIS.z;
        disk.yAxis.z=NORMAL.x*AXIS.y-NORMAL.y*AXIS.x;
        disk.xRadius=disk.yRadius=.23f;
        disk.shaping_hasvalue=true;disk.shaping_coneAngleDegrees=35;
        disk.shaping_coneSoftness=.1f;disk.shaping_focusExponent=0;disk.volumetricRadianceScale=1;
        int value=(int)(state>>>(4+slot*6)&63);
        int color=value<2?0xfff5dc:
                    DyeColor.byId(Math.min(15, value - 2)).getEntityColor();
                    info.radiance.x = ((color >>> 16) & 255) / 255f * 600;
                    info.radiance.y = ((color >>> 8) & 255) / 255f * 600;
                    info.radiance.z = (color & 255) / 255f * 600;
            }
        }

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

/** Copycat models resolve per-material occlusion while collecting their model data. */
final class ModelFaceCulling {
    private static final ClassValue<Boolean> COPYCAT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return implementsInterface(type, "com.copycatsplus.copycats.foundation.copycat.ICopycatBlock");
        }
    };
    private ModelFaceCulling() {}

    static boolean ownsOcclusion(Class<?> blockClass) {
        return COPYCAT.get(blockClass);
    }

    static boolean implementsInterface(Class<?> type, String name) {
        if (type == null)
            return false;
        if (type.isInterface() && type.getName().equals(name))
            return true;
        for (Class<?> parent : type.getInterfaces())
            if (implementsInterface(parent, name))
                return true;
        return implementsInterface(type.getSuperclass(), name);
    }
}

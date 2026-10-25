/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.terrain.capture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelFaceCullingTest {
    interface MaterialModel {}
    interface MultiMaterialModel extends MaterialModel {}
    static class Base implements MultiMaterialModel {}
    static class Layer extends Base {}
    @Test
    void inheritedMaterialInterfacesAreFoundWithoutLoadingOptionalModClasses() {
        assertTrue(ModelFaceCulling.implementsInterface(Layer.class, MaterialModel.class.getName()));
        assertTrue(ModelFaceCulling.implementsInterface(Base.class, MultiMaterialModel.class.getName()));
        assertFalse(ModelFaceCulling.implementsInterface(Object.class, MaterialModel.class.getName()));
        assertFalse(ModelFaceCulling.implementsInterface(Layer.class, "other.MaterialModel"));
        assertFalse(ModelFaceCulling.ownsOcclusion(Layer.class));
    }
}

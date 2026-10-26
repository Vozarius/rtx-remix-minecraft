/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nvidia.rtxremix.minecraft.compat.dh.*;
import com.seibel.distanthorizons.core.dataObjects.render.bufferBuilding.LodBufferContainer;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(value = LodBufferContainer.class, remap = false)
public abstract class DistantBufferMixin implements DistantBufferAccess {
    @Unique private volatile DistantLodData rtxremix$data;
    @Unique private volatile boolean rtxremix$closed;
    public DistantLodData rtxremix$data() {
        return rtxremix$data;
    }
    public synchronized void rtxremix$setData(DistantLodData data) {
        if (rtxremix$data != null && rtxremix$data != data)
            rtxremix$data.closed = true;
        data.closed = rtxremix$closed;
        rtxremix$data = data;
    }
    @WrapMethod(method = "tryMakeAndUploadBuffersAsync")
    private static CompletableFuture<LodBufferContainer> rtxremix$copy(long pos, IClientLevelWrapper level,
            ArrayList<ByteBuffer> opaque, ArrayList<ByteBuffer> translucent,
            Operation<CompletableFuture<LodBufferContainer>> original) {
        DistantLodData data = new DistantLodData(DhSectionPos.getMinCornerBlockX(pos), level.getMinHeight(),
                DhSectionPos.getMinCornerBlockZ(pos), DhSectionPos.getBlockWidth(pos), opaque, translucent);
        return original.call(pos, level, opaque, translucent).whenComplete((container, error) -> {
            if (error != null)
                data.closed = true;
            else
                ((DistantBufferAccess) container).rtxremix$setData(data);
        });
    }
    @Inject(method = "close", at = @At("HEAD"))
    private synchronized void rtxremix$close(CallbackInfo ci) {
        rtxremix$closed = true;
        if (rtxremix$data != null)
            rtxremix$data.closed = true;
    }
}

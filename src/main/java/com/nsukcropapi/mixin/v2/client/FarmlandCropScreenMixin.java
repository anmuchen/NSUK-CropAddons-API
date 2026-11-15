package com.nsukcropapi.mixin.v2.client;

import client.cn.nsimu.simukraft.client.farmland.FarmlandCropScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.client.CropSelectScreen;
import common.cn.nsimu.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 用统一的作物选择界面替换原版界面。
 */
@Mixin(value = FarmlandCropScreen.class, remap = false)
public abstract class FarmlandCropScreenMixin {

    @WrapMethod(method = "open")
    private static void nsukcropapi$open(FarmlandBoxOpenResponsePacket packet, Operation<Void> original) {
        CropSelectScreen.open(packet);
    }
}

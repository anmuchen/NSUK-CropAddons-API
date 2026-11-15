package com.nsukcropapi.mixin.v2.client;

import client.cn.nsimu.simukraft.client.farmland.FarmlandBoxScreenOpener;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import common.cn.nsimu.simukraft.network.farmland.FarmlandBoxOpenResponsePacket;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 农田盒主界面显示 API 作物名。
 */
@Mixin(value = FarmlandBoxScreenOpener.class, remap = false)
public abstract class FarmlandBoxScreenOpenerMixin {

    @WrapMethod(method = "cropLine")
    private static Component nsukcropapi$cropLine(FarmlandBoxOpenResponsePacket packet, Operation<Component> original) {
        CropSpec spec = CropRegistry.byId(packet.cropId());
        if (spec != null) {
            return Component.translatable("gui.simukraft.farmland_box.crop_line",
                    Component.translatable(spec.displayNameKey()));
        }
        return original.call(packet);
    }
}

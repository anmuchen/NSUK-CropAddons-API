package com.nsukcropapi.mixin.v2;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.NsukCropApi;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.CropWork;
import com.nsukcropapi.api.CropWorkContext;
import com.nsukcropapi.nsuk.CropContext;
import com.nsukcropapi.nsuk.CropWorkContextHolder;
import com.nsukcropapi.nsuk.FarmlandFarmingEngine;
import com.nsukcropapi.nsuk.FarmlandBoxDataExt;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandBoxManager;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * 在农田盒作业流程内激活作物上下文，并给作物一个覆盖整个 tickBox 的作用域钩子。
 */
@Mixin(value = FarmlandFarmingService.class, remap = false)
public abstract class FarmlandFarmingServiceMixin {

    @WrapMethod(method = "tickBox")
    private static void nsukcropapi$tickBox(ServerLevel level, FarmlandBoxManager manager, FarmlandBoxData data,
                                            @Coerce Object boxRuntime, long gameTime, Operation<Void> original) {
        CropSpec spec = data == null
                ? null
                : CropRegistry.byId(((FarmlandBoxDataExt) (Object) data).nsukcropapi$getCropId());
        if (spec == null) {
            original.call(level, manager, data, boxRuntime, gameTime);
            return;
        }
        CropWork work = spec.workOrDefault();
        CropWorkContext context = FarmlandFarmingEngine.context(level, data);
        CropContext.push(spec);
        CropWorkContextHolder.push(context);
        try {
            try {
                work.onTickStart(context, spec);
            } catch (Throwable t) {
                NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的 tick 进入钩子抛异常，已忽略", spec.id(), t);
            }
            original.call(level, manager, data, boxRuntime, gameTime);
        } finally {
            try {
                work.onTickEnd(context, spec);
            } catch (Throwable t) {
                NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的 tick 退出钩子抛异常，已忽略", spec.id(), t);
            }
            CropWorkContextHolder.clear();
            CropContext.pop();
        }
    }
}

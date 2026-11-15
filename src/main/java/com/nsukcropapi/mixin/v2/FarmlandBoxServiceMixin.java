package com.nsukcropapi.mixin.v2;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.nsuk.AddonArbitration;
import com.nsukcropapi.nsuk.FarmlandBoxDataExt;
import common.cn.nsimu.simukraft.farmland.FarmCrop;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxData;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxManager;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxService;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 让农田盒可以选择/显示 API 注册的作物。
 */
@Mixin(value = FarmlandBoxService.class, remap = false)
public abstract class FarmlandBoxServiceMixin {

    @WrapMethod(method = "setCrop")
    private static boolean nsukcropapi$setCrop(ServerLevel level, BlockPos boxPos, String cropId, Operation<Boolean> original) {
        CropSpec spec = CropRegistry.byId(cropId);
        if (spec != null) {
            FarmlandBoxManager manager = FarmlandBoxManager.get(level);
            FarmlandBoxData data = manager.getOrCreate(boxPos);
            if (data.running()) {
                return false;
            }
            AddonArbitration.clearForeignIds(data);
            data.setCrop(FarmCrop.WHEAT);
            ((FarmlandBoxDataExt) (Object) data).nsukcropapi$setCropId(spec.id());
            manager.persist(data);
            return true;
        }

        boolean result = original.call(level, boxPos, cropId);
        if (result) {
            FarmlandBoxManager manager = FarmlandBoxManager.get(level);
            FarmlandBoxData data = manager.get(boxPos);
            if (data != null) {
                boolean changed = false;
                FarmlandBoxDataExt ext = (FarmlandBoxDataExt) (Object) data;
                if (!ext.nsukcropapi$getCropId().isBlank()) {
                    ext.nsukcropapi$setCropId("");
                    changed = true;
                }
                if (FarmCrop.fromId(cropId) != null) {
                    changed |= AddonArbitration.clearForeignIds(data);
                }
                if (changed) {
                    manager.persist(data);
                }
            }
        }
        return result;
    }

    @WrapMethod(method = "buildView")
    private static FarmlandBoxView nsukcropapi$buildView(ServerLevel level, BlockPos boxPos, Operation<FarmlandBoxView> original) {
        FarmlandBoxView view = original.call(level, boxPos);
        if (view == null) {
            return null;
        }
        FarmlandBoxData data = FarmlandBoxManager.get(level).get(boxPos);
        if (data == null) {
            return view;
        }
        String cropId = ((FarmlandBoxDataExt) (Object) data).nsukcropapi$getCropId();
        if (cropId.isBlank()) {
            return view;
        }
        return new FarmlandBoxView(
                view.boxPos(),
                view.hasCity(),
                cropId,
                view.hasPlot(),
                view.plotMin(),
                view.plotMax(),
                view.hasChest(),
                view.chestPos(),
                view.running(),
                view.hasFarmer(),
                view.farmerName()
        );
    }
}

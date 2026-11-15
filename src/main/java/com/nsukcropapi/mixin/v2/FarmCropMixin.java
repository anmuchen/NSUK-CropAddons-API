package com.nsukcropapi.mixin.v2;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.api.CropRules;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.CropWork;
import com.nsukcropapi.nsuk.CropContext;
import common.cn.nsimu.simukraft.farmland.FarmCrop;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 把 FarmCrop 占位枚举的方法委托给 API 注册的作物。
 */
@Mixin(value = FarmCrop.class, remap = false)
public abstract class FarmCropMixin {

    @WrapMethod(method = "seed")
    private Item nsukcropapi$seed(Operation<Item> original) {
        CropSpec spec = CropContext.current();
        return spec != null ? spec.seed() : original.call();
    }

    @WrapMethod(method = "layout")
    private FarmCrop.Layout nsukcropapi$layout(Operation<FarmCrop.Layout> original) {
        return CropContext.current() != null ? FarmCrop.Layout.FULL : original.call();
    }

    @WrapMethod(method = "isStem")
    private boolean nsukcropapi$isStem(Operation<Boolean> original) {
        return CropContext.current() != null ? false : original.call();
    }

    @WrapMethod(method = "id")
    private String nsukcropapi$id(Operation<String> original) {
        CropSpec spec = CropContext.current();
        return spec != null ? spec.id() : original.call();
    }

    @WrapMethod(method = "translationKey")
    private String nsukcropapi$translationKey(Operation<String> original) {
        CropSpec spec = CropContext.current();
        return spec != null ? spec.displayNameKey() : original.call();
    }

    @WrapMethod(method = "shouldPlantAt")
    private boolean nsukcropapi$shouldPlantAt(int x, int z, Operation<Boolean> original) {
        return CropContext.current() != null ? true : original.call(x, z);
    }

    @WrapMethod(method = "plantState")
    private BlockState nsukcropapi$plantState(Operation<BlockState> original) {
        CropSpec spec = CropContext.current();
        if (spec == null) {
            return original.call();
        }
        BlockState state = spec.workOrDefault().plantState(spec);
        return state != null ? state : CropRules.plantState(spec);
    }

    @WrapMethod(method = "plantBlock")
    private Block nsukcropapi$plantBlock(Operation<Block> original) {
        CropSpec spec = CropContext.current();
        return spec != null ? spec.plantBlock() : original.call();
    }

    @WrapMethod(method = "produceBlock")
    private Block nsukcropapi$produceBlock(Operation<Block> original) {
        return CropContext.current() != null ? null : original.call();
    }

    @WrapMethod(method = "isOwnPlant")
    private boolean nsukcropapi$isOwnPlant(BlockState state, Operation<Boolean> original) {
        CropSpec spec = CropContext.current();
        return spec != null ? spec.workOrDefault().isOwnPlant(state, spec) : original.call(state);
    }

    @WrapMethod(method = "isMatureFull")
    private boolean nsukcropapi$isMatureFull(BlockState state, Operation<Boolean> original) {
        CropSpec spec = CropContext.current();
        if (spec == null) {
            return original.call(state);
        }
        com.nsukcropapi.api.CropWorkContext ctx = com.nsukcropapi.nsuk.CropWorkContextHolder.current();
        return spec.workOrDefault().isMature(
                ctx != null ? ctx : com.nsukcropapi.api.CropWorkContext.minimal(null, state), state, spec);
    }

    @WrapMethod(method = "isProduce")
    private boolean nsukcropapi$isProduce(BlockState state, Operation<Boolean> original) {
        return CropContext.current() != null ? false : original.call(state);
    }
}

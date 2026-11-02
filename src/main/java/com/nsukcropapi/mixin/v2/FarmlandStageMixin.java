package com.nsukcropapi.mixin.v2;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.api.StageOutcome;
import com.nsukcropapi.nsuk.FarmlandFarmingEngine;
import com.nsukcropapi.nsuk.WorkOutcomes;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import common.cn.kafei.simukraft.farmland.FarmlandFarmingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 五阶段接管：把宿主的 DIG_WATER / TILL / PLANT / BONEMEAL / HARVEST 判定与执行转给作物。
 *
 * <p>每个阶段都是"先问作物、返回 null 就原样调用宿主"。执行阶段用 HEAD 取消而不是 WrapMethod，
 * 是因为宿主的结果枚举 {@code FarmlandWorkResult} 包内可见，源码里无法声明该返回类型。</p>
 */
@Mixin(value = FarmlandFarmingService.class, remap = false)
public abstract class FarmlandStageMixin {

    // ---------------- DIG_WATER ----------------

    @WrapMethod(method = "needsWaterWork")
    private static boolean nsukcropapi$needsWaterWork(ServerLevel level, FarmlandBoxData data,
                                                      List<BlockPos> chests, BlockPos cropPos,
                                                      Operation<Boolean> original) {
        Boolean verdict = FarmlandFarmingEngine.needsWater(level, data, chests, cropPos);
        return verdict != null ? verdict : original.call(level, data, chests, cropPos);
    }

    @Inject(method = "applyWaterWork", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$applyWaterWork(ServerLevel level, FarmlandBoxData data,
                                                   List<BlockPos> chests, BlockPos cropPos,
                                                   CallbackInfoReturnable<Object> cir) {
        nsukcropapi$finish(cir, FarmlandFarmingEngine.applyWater(level, data, chests, cropPos));
    }

    // ---------------- TILL ----------------

    @WrapMethod(method = "needsTillWork")
    private static boolean nsukcropapi$needsTillWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                     Operation<Boolean> original) {
        Boolean verdict = FarmlandFarmingEngine.needsTill(level, data, cropPos);
        return verdict != null ? verdict : original.call(level, data, cropPos);
    }

    @Inject(method = "applyTillWork", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$applyTillWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                  CallbackInfoReturnable<Object> cir) {
        nsukcropapi$finish(cir, FarmlandFarmingEngine.applyTill(level, data, cropPos));
    }

    // ---------------- PLANT ----------------

    @WrapMethod(method = "needsPlantWork")
    private static boolean nsukcropapi$needsPlantWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                      Operation<Boolean> original) {
        Boolean verdict = FarmlandFarmingEngine.needsPlant(level, data, cropPos);
        return verdict != null ? verdict : original.call(level, data, cropPos);
    }

    @Inject(method = "applyPlantWork", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$applyPlantWork(ServerLevel level, FarmlandBoxData data,
                                                   List<BlockPos> chests, BlockPos cropPos,
                                                   CallbackInfoReturnable<Object> cir) {
        nsukcropapi$finish(cir, FarmlandFarmingEngine.applyPlant(level, data, chests, cropPos));
    }

    /** 箱里没有种子时宿主会整段跳过 PLANT，藤架这类"先搭架子后插藤"的作物需要自己回答。 */
    @WrapMethod(method = "hasSeed")
    private static boolean nsukcropapi$hasSeed(ServerLevel level, List<BlockPos> chests, FarmCrop crop,
                                               Operation<Boolean> original) {
        Boolean verdict = FarmlandFarmingEngine.seedAvailable(level, chests);
        return verdict != null ? verdict : original.call(level, chests, crop);
    }

    // ---------------- BONEMEAL ----------------

    @WrapMethod(method = "needsBonemealWork")
    private static boolean nsukcropapi$needsBonemealWork(ServerLevel level, FarmlandBoxData data,
                                                         List<BlockPos> chests, BlockPos cropPos,
                                                         Operation<Boolean> original) {
        Boolean verdict = FarmlandFarmingEngine.needsBonemeal(level, data, chests, cropPos);
        return verdict != null ? verdict : original.call(level, data, chests, cropPos);
    }

    @Inject(method = "applyBonemealWork", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$applyBonemealWork(ServerLevel level, FarmlandBoxData data,
                                                      List<BlockPos> chests, BlockPos cropPos,
                                                      CallbackInfoReturnable<Object> cir) {
        nsukcropapi$finish(cir, FarmlandFarmingEngine.applyBonemeal(level, data, chests, cropPos));
    }

    // ---------------- HARVEST ----------------

    @WrapMethod(method = "needsHarvestWork")
    private static boolean nsukcropapi$needsHarvestWork(ServerLevel level, FarmlandBoxData data, BlockPos cropPos,
                                                        Operation<Boolean> original) {
        Boolean verdict = FarmlandFarmingEngine.needsHarvest(level, data, cropPos);
        return verdict != null ? verdict : original.call(level, data, cropPos);
    }

    @Inject(method = "applyHarvestWork", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$applyHarvestWork(ServerLevel level, FarmlandBoxData data,
                                                     List<BlockPos> chests, BlockPos cropPos,
                                                     CallbackInfoReturnable<Object> cir) {
        nsukcropapi$finish(cir, FarmlandFarmingEngine.applyHarvest(level, data, chests, cropPos));
    }

    // ---------------- 配套：状态文案 / 扫描模式 ----------------

    @WrapMethod(method = "farmerStatusLabel")
    private static String nsukcropapi$farmerStatusLabel(ServerLevel level, String statusKey, FarmCrop crop,
                                                        Operation<String> original) {
        String label = FarmlandFarmingEngine.statusLabel(level, statusKey);
        return label != null ? label : original.call(level, statusKey, crop);
    }

    @Inject(method = "usesGroupedFarmScan", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$usesGroupedFarmScan(CallbackInfoReturnable<Boolean> cir) {
        Boolean verdict = FarmlandFarmingEngine.usesGroupedFarmScan();
        if (verdict != null) {
            cir.setReturnValue(verdict);
        }
    }

    /**
     * 把结果写回宿主。拿不到宿主枚举常量时直接放行，让宿主原逻辑继续跑（降级而不是崩溃）。
     */
    @Unique
    private static void nsukcropapi$finish(CallbackInfoReturnable<Object> cir, StageOutcome outcome) {
        if (outcome == null) {
            return;
        }
        Object result = WorkOutcomes.of(outcome);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}

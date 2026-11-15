package com.nsukcropapi.mixin.v2;

import com.nsukcropapi.crop.TrellisCropWork;
import com.nsukcropapi.nsuk.TrellisGate;
import common.cn.nsimu.simukraft.citizen.CitizenData;
import common.cn.nsimu.simukraft.citizen.CitizenTeleportService;
import common.cn.nsimu.simukraft.entity.CitizenEntity;
import common.cn.nsimu.simukraft.farmland.FarmlandFarmingService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 藤架作物的可达性接管：宿主判定"走不到"时，把雇农放到藤架旁的落脚点。
 *
 * <p>藤架类作物的作业点在架子顶端，雇农按常规寻路会站在地头反复重试，
 * 表现为"一直显示走不过去、永远不动手"。这里在宿主给出否定答案后补一次：
 * 先让雇农自己走 40 tick（正常寻路优先），之后每 10 tick 允许一次传送。</p>
 *
 * <p>只在作物通过 {@code CropWork#usesTrellisWork} 声明藤架几何时生效，
 * 普通耕地作物完全不受影响。</p>
 */
@Mixin(value = FarmlandFarmingService.class, remap = false)
public abstract class FarmlandReachMixin {

    @Inject(method = "isCloseEnoughToWork", at = @At("RETURN"), cancellable = true)
    private static void nsukcropapi$trellisReach(ServerLevel level, BlockPos boxPos, CitizenData farmer,
                                                 CitizenEntity farmerEntity, @Coerce Object target,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || level == null || boxPos == null || farmer == null || farmerEntity == null) {
            return;
        }
        if (!TrellisGate.active()) {
            return;
        }
        BlockPos cropPos = TrellisGate.cropPosOf(target);
        if (cropPos == null) {
            return;
        }
        long now = level.getGameTime();
        if (!TrellisGate.allowTeleport(farmer.uuid(), boxPos, now)) {
            return;
        }
        Vec3 anchor = TrellisCropWork.teleportAnchor(level, cropPos);
        if (anchor == null) {
            return;
        }
        boolean teleported = false;
        try {
            teleported = CitizenTeleportService.teleportLoadedCitizen(level, farmer, anchor);
        } catch (Throwable ignored) {
            // 宿主传送接口不可用时退回到直接改位置
        }
        if (!teleported) {
            farmerEntity.getNavigation().stop();
            farmerEntity.setDeltaMovement(Vec3.ZERO);
            farmerEntity.teleportTo(anchor.x, anchor.y, anchor.z);
            teleported = farmerEntity.position().distanceToSqr(anchor) <= 1.0;
        }
        TrellisGate.markTeleported(farmer.uuid(), now);
        if (teleported) {
            cir.setReturnValue(true);
        }
    }
}

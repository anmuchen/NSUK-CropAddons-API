package com.nsukcropapi.mixin.v2;

import com.nsukcropapi.crop.TrellisCropWork;
import com.nsukcropapi.nsuk.TrellisGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 藤架作物的站位接管：把雇农的作业落脚点从地头换到藤架下的可站立位置。
 *
 * <p>宿主的 {@code workAnchorFor} 只按农田盒格子的常规几何算站位，藤架的作业点在
 * 架子顶端，算出来的点雇农够不着。这里在作业点属于藤架作物时改用
 * {@link TrellisCropWork#standAnchor}；该值返回 {@code null} 表示没找到合适位置，
 * 交回宿主原值。</p>
 *
 * <p>目标类是包内可见的 final class，所以用字符串 target 而不是类字面量。</p>
 */
@Mixin(targets = "common.cn.nsimu.simukraft.farmland.FarmlandWorkGeometry", remap = false)
public abstract class FarmlandWorkGeometryMixin {

    @Inject(method = "workAnchorFor", at = @At("HEAD"), cancellable = true)
    private static void nsukcropapi$trellisStandAnchor(ServerLevel level, BlockPos boxPos, BlockPos cropPos,
                                                       CallbackInfoReturnable<Vec3> cir) {
        if (level == null || cropPos == null || !TrellisGate.active()) {
            return;
        }
        Vec3 anchor = TrellisCropWork.standAnchor(level, cropPos);
        if (anchor != null) {
            cir.setReturnValue(anchor);
        }
    }
}

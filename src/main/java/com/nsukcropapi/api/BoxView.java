package com.nsukcropapi.api;

import com.nsukcropapi.nsuk.FarmlandBoxDataExt;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxData;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * 农田盒的中性视图：位置、地块几何、当前作物 id、是否运行中。
 *
 * <p>附属拿到的都是 Minecraft 自带类型（{@link BlockPos} / {@link String} / {@code boolean}），
 * 因此源码里不会出现 simukraft 的类名，宿主改名或改包时附属不需要跟着改。</p>
 *
 * @param boxPos  农田盒方块位置
 * @param plot    圈地几何；还没圈地时为 {@code null}
 * @param cropId  存档里记录的作物 id；未设置为空串
 * @param running 雇农是否正在作业
 */
public record BoxView(BlockPos boxPos, PlotView plot, String cropId, boolean running) {

    /** 从宿主的盒数据对象构造；不是农田盒数据时返回 {@code null}。 */
    public static BoxView of(Object data) {
        if (!(data instanceof FarmlandBoxData box)) {
            return null;
        }
        String cropId = "";
        try {
            String stored = ((FarmlandBoxDataExt) (Object) box).nsukcropapi$getCropId();
            if (stored != null) {
                cropId = stored;
            }
        } catch (Throwable ignored) {
            // 宿主接口缺失时按"未设置作物"处理，不影响几何与位置的使用
        }
        return new BoxView(box.boxPos(), PlotView.of(box.plot()), cropId, box.running());
    }

    /** 该盒关联的容器位置；取不到时返回空列表而不是 {@code null}。 */
    public List<BlockPos> chests(ServerLevel level) {
        if (level == null || this.boxPos == null) {
            return List.of();
        }
        try {
            List<BlockPos> found = FarmlandBoxService.resolveAdjacentChests(level, this.boxPos);
            return found == null ? List.of() : found;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /** 是否已圈地（几何可用）。 */
    public boolean hasPlot() {
        return this.plot != null;
    }
}

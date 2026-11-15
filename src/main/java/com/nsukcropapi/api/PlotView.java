package com.nsukcropapi.api;

import common.cn.nsimu.simukraft.farmland.FarmlandPlot;
import net.minecraft.core.BlockPos;

/**
 * 农田盒地块几何的中性视图。
 *
 * <p>把宿主的 {@code FarmlandPlot} 摊平成只含 {@code BlockPos} 与 {@code int} 的只读数据，
 * 附属因此不需要 import 任何 simukraft 类型就能算格子、判水槽行、找四角。</p>
 *
 * <p>遍历顺序与宿主一致：按行（z）优先，奇数行反向（蛇形），因此
 * {@link #cellAt(int)} 与宿主的 {@code FarmlandPlot#cellAt(int)} 结果相同。
 * 两者不一致会让雇农与附属对同一格的理解产生偏差。</p>
 */
public record PlotView(BlockPos min, BlockPos max) {

    /** 从一个 {@code FarmlandPlot} 实例构造；类型不匹配时返回 {@code null}。 */
    public static PlotView of(Object plot) {
        if (plot instanceof FarmlandPlot farmlandPlot) {
            return new PlotView(farmlandPlot.min(), farmlandPlot.max());
        }
        return null;
    }

    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int depth() {
        return max.getZ() - min.getZ() + 1;
    }

    public int cellCount() {
        return width() * depth();
    }

    /** 第 {@code index} 格（蛇形顺序，越界自动取模）；几何退化时落在 {@link #min}。 */
    public BlockPos cellAt(int index) {
        int cells = Math.max(1, cellCount());
        int normalized = Math.floorMod(index, cells);
        int row = normalized / width();
        int column = normalized % width();
        if (row % 2 == 1) {
            column = width() - 1 - column;
        }
        return new BlockPos(min.getX() + column, min.getY(), min.getZ() + row);
    }

    /** 该坐标的 X/Z 是否落在地块范围内（不看 Y）。 */
    public boolean containsColumn(BlockPos pos) {
        return pos != null
                && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }
}

package com.nsukcropapi.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 作业上下文：把附属需要的运行期信息（箱子、地块、农田盒数据）统一传给 {@link CropWork}。
 *
 * @param level   服务端世界
 * @param boxPos  农田盒位置（可能为空，取决于调用点）
 * @param cropPos 当前作物位置
 * @param chests  农田盒关联的容器位置（种子/骨粉来源）
 * @param data    农田盒数据对象（API 不直接依赖 simukraft 类型，用 Object 承载）
 * @param soil    当前作物脚下的土壤方块状态
 */
public record CropWorkContext(
        ServerLevel level,
        BlockPos boxPos,
        BlockPos cropPos,
        List<BlockPos> chests,
        Object data,
        BlockState soil
) {

    public static CropWorkContext minimal(ServerLevel level, BlockState soil) {
        return new CropWorkContext(level, null, null, List.of(), null, soil);
    }

    /** 箱子列表永不为 null，附属可以放心遍历。 */
    @Override
    public List<BlockPos> chests() {
        return this.chests == null ? List.of() : this.chests;
    }

    /** 复制一份上下文，补上当前处理的格子与脚下土壤（种子/箱子的来源沿用原上下文）。 */
    public CropWorkContext at(BlockPos cropPos, BlockState soil) {
        return new CropWorkContext(this.level, this.boxPos, cropPos, this.chests(), this.data, soil);
    }
}

package com.nsukcropapi.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 作物作业策略：把"怎么种、怎么判定成熟、怎么收"从 API 中抽出。
 * 默认实现是"普通耕地 + CropBlock"，葡萄架 / 自定义土壤 / 双高作物等由附属提供自己的实现。
 *
 * <p><b>五阶段接管</b>：农田盒的作业流程分 DIG_WATER / TILL / PLANT / BONEMEAL / HARVEST 五个阶段，
 * 每个阶段由一对方法描述 —— {@code needsXxx} 回答"这一格现在需不需要做"，{@code applyXxx} 真正去做。
 * 两个方法都返回 {@code null} 表示"本附属不接管这一阶段"，宿主按原有逻辑处理。</p>
 *
 * <p><b>契约</b>：一旦 {@code needsXxx} 返回非 {@code null}，就<b>必须</b>同时实现 {@code applyXxx}；
 * 只接管一半会让这一格被反复选中却永远做不成事，雇农会永久卡死在这一阶段。
 * 拿不准需不需要做时返回 {@code Boolean.FALSE}，而不是让 {@code applyXxx} 落空。</p>
 *
 * <p><b>水槽行</b>（每 4 格 z 里的第 4 行）默认不归作物管：即使接管了 TILL / PLANT / BONEMEAL / HARVEST，
 * 引擎也会对这些格子回答"不需要"，避免雇农为了给水槽铺土而永久卡在翻地阶段。
 * 只有 DIG_WATER 阶段允许接管水槽行。</p>
 */
public interface CropWork {

    /** 普通耕地作物（CropBlock，age 0..7）。 */
    CropWork PLAIN = new CropWork() {
    };

    /**
     * 该作物的农田盒开始处理一个 tick 时调用一次。
     *
     * <p>用来给"整段作业期间都要成立"的状态占位（例如藤架要靠自己的上下文改写宿主站位），
     * 与逐阶段的 {@code needsXxx} 不同：这个钩子覆盖 tickBox 全程。</p>
     */
    default void onTickStart(CropWorkContext ctx, CropSpec spec) {
    }

    /** 该作物的农田盒处理完一个 tick 时调用一次；即使 tickBox 抛异常也会调用。 */
    default void onTickEnd(CropWorkContext ctx, CropSpec spec) {
    }

    /**
     * 播种时放置的方块状态。
     *
     * <p>返回 {@code null} 用 {@link CropRules#plantState}（方块默认状态）。
     * 需要"种下去就带成熟年龄"、或把品种写在方块状态上的作物（葡园葡萄的 {@code grape=red}）
     * 必须实现这里，否则宿主的 PLANT 阶段会种出错误的方块状态。</p>
     */
    default BlockState plantState(CropSpec spec) {
        return null;
    }

    /** 默认：直接放置作物方块的初始状态。 */
    default boolean plant(CropWorkContext ctx, CropSpec spec) {
        if (ctx.level() == null || ctx.cropPos() == null) {
            return false;
        }
        return ctx.level().setBlock(ctx.cropPos(), CropRules.plantState(spec), 3);
    }

    /** 默认：age 满即成熟。 */
    default boolean isMature(CropWorkContext ctx, BlockState state, CropSpec spec) {
        return CropRules.isMatureFull(spec, state);
    }

    /** 默认：按战利品表收掉方块，并把掉落物回传（由调用方存进箱子）。 */
    default List<ItemStack> harvest(CropWorkContext ctx, BlockState state, CropSpec spec) {
        List<ItemStack> drops = new ArrayList<>();
        if (ctx.level() == null || ctx.cropPos() == null) {
            return drops;
        }
        drops.addAll(Block.getDrops(state, ctx.level(), ctx.cropPos(), ctx.level().getBlockEntity(ctx.cropPos())));
        ctx.level().setBlock(ctx.cropPos(), Blocks.AIR.defaultBlockState(), 3);
        return drops;
    }

    /** 该方块位置是否算"本作物的作物本体"（多方块结构可覆盖）。 */
    default boolean isOwnPlant(BlockState state, CropSpec spec) {
        return CropRules.isOwnPlant(spec, state);
    }

    /** 是否允许在指定位置播种（土壤类型、高度限制等）。 */
    default boolean canPlantAt(CropWorkContext ctx, BlockState soil, CropSpec spec) {
        return true;
    }

    /** 兼容旧调用点：无上下文的成熟判定。 */
    default boolean isMature(BlockState state, CropSpec spec) {
        return isMature(CropWorkContext.minimal(null, state), state, spec);
    }

    // ------------------------------------------------------------------
    // 五阶段接管：返回 null = 交回宿主原逻辑
    // ------------------------------------------------------------------

    /** DIG_WATER：水槽格要不要放水。 */
    default Boolean needsWater(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** DIG_WATER：放水（通常会清掉格内作物/方块）。 */
    default StageOutcome applyWater(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** TILL：作物格下方要不要翻成/换成该作物的土壤。 */
    default Boolean needsTill(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** TILL：翻地 / 铺土。 */
    default StageOutcome applyTill(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** PLANT：这一格现在要不要播种。 */
    default Boolean needsPlant(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** PLANT：播种（含多方块结构的建造，缺材料请返回 {@link StageOutcome#WAITING_MATERIAL}）。 */
    default StageOutcome applyPlant(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** BONEMEAL：这一格能不能施骨粉。 */
    default Boolean needsBonemeal(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** BONEMEAL：催熟。 */
    default StageOutcome applyBonemeal(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** HARVEST：这一格是否已经可以收。 */
    default Boolean needsHarvest(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /** HARVEST：收割并存入箱子（补种也由本方法负责）。 */
    default StageOutcome applyHarvest(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /**
     * 箱子里的"种子"够不够，用来决定宿主是否扫描 PLANT 阶段。
     *
     * <p>返回 {@code null} 时宿主按 {@link CropSpec#seed()} 是否存在于箱子判断。
     * 藤架这类"先搭架子、后插藤"的作物应该返回 {@code Boolean.TRUE}，
     * 否则箱里没有种子时连架子都不会被搭起来。</p>
     */
    default Boolean seedAvailable(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /**
     * 雇农头顶的状态文案。
     *
     * <p>宿主传进来的 {@code statusKey} 是它自己的语言键（如
     * {@code gui.simukraft.farmland.status.missing_seed} / {@code .till} / {@code .plant}），
     * 返回 {@code null} 就用宿主文案。缺的是"藤架/土壤"而不是种子时，
     * 在这里换成自己的键，头顶才不会一直显示"等待种子：xxx"。</p>
     */
    default net.minecraft.network.chat.Component statusLabel(CropWorkContext ctx, String statusKey, CropSpec spec) {
        return null;
    }

    /**
     * 是否沿用宿主的"分组扫描"（每 4 格 z 跳过第 4 行水槽）。
     *
     * <p>返回 {@code null} 用宿主默认（TILL / PLANT / BONEMEAL / HARVEST 都按分组扫描）。
     * 自定义土壤这类"不需要水槽、整块地都要种"的作物返回 {@code Boolean.FALSE}，
     * 扫描范围才会覆盖到全部格子。</p>
     */
    default Boolean usesGroupedFarmScan(CropWorkContext ctx, CropSpec spec) {
        return null;
    }

    /**
     * 水渠占位行是否也允许本作物作业。
     *
     * <p>默认 false：宿主把每 4 行里的第 4 行留作水槽，非 DIG_WATER 阶段一律跳过。
     * 不需要任何土壤的作物（马勃菌）把它答成 true，那一行也会照常播种/收割。</p>
     */
    default boolean allowWaterTroughCells(CropWorkContext ctx, CropSpec spec) {
        return false;
    }

    /** 是否从作物选择界面隐藏（存档里已有的农田盒仍可继续使用该作物）。 */
    default boolean hiddenFromUi(CropSpec spec) {
        return false;
    }

    /** 便捷方法：给定世界与坐标时的最小上下文。 */
    static CropWorkContext at(ServerLevel level, BlockPos pos, BlockState soil) {
        return new CropWorkContext(level, null, pos, List.of(), null, soil);
    }
}

package com.nsukcropapi.api;

import com.nsukcropapi.crop.CropAge;
import com.nsukcropapi.crop.FarmlandFillWork;
import com.nsukcropapi.nsuk.WorkContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 「普通耕地作物 + 自动铺地 + 不挖水渠」的作业策略。
 *
 * <p>宿主的 TILL 只认草方块 / 泥土 / 砂土 / 灰化土 / 缠根泥土这五种（见
 * {@code FarmlandFarmingService.isTillable}）。农田盒搭在石头、沙砾、深板岩平台上时，
 * 箱子里备着泥土宿主也不会铺，整片地只在原有土块上零星种几株。本策略改用
 * {@link FarmlandFillWork}：泥土类直接翻地，其它方块从箱子取一份泥土替换，
 * 从而把选定区域整块铺满。</p>
 *
 * <p>同时接管水渠：宿主默认每 4 格 z 的第 4 行留作水槽并主动灌水，普通作物并不需要。
 * 这里把 DIG_WATER 答成「不用做」，再放开分组扫描与水渠行限制，整块地都参与
 * 翻地 / 播种 / 收割；已经挖成水渠的格子（下方是流体）不会被回填。</p>
 *
 * <p>播种与收割必须自己实现：宿主的 {@code needsPlantWork} / {@code needsHarvestWork}
 * 第一行就把水渠行排除掉了，只放开扫描范围而不接管这两个阶段，水渠行仍会空一列。</p>
 */
public final class FarmlandCropWork implements CropWork {

    public static final FarmlandCropWork INSTANCE = new FarmlandCropWork();

    private FarmlandCropWork() {
    }

    @Override
    public Boolean needsWater(CropWorkContext ctx, CropSpec spec) {
        return Boolean.FALSE;
    }

    @Override
    public StageOutcome applyWater(CropWorkContext ctx, CropSpec spec) {
        return StageOutcome.SKIPPED;
    }

    @Override
    public Boolean needsTill(CropWorkContext ctx, CropSpec spec) {
        if (ctx == null || ctx.level() == null || ctx.cropPos() == null) {
            return Boolean.FALSE;
        }
        return FarmlandFillWork.needsTill(ctx.level(), ctx.cropPos());
    }

    @Override
    public StageOutcome applyTill(CropWorkContext ctx, CropSpec spec) {
        if (!Boolean.TRUE.equals(needsTill(ctx, spec))) {
            return StageOutcome.SKIPPED;
        }
        return FarmlandFillWork.applyTill(ctx.level(), ctx.chests(), ctx.cropPos())
                ? StageOutcome.PROCESSED
                : StageOutcome.WAITING_MATERIAL;
    }

    @Override
    public Boolean needsPlant(CropWorkContext ctx, CropSpec spec) {
        if (ctx == null || ctx.level() == null || ctx.cropPos() == null) {
            return Boolean.FALSE;
        }
        BlockState cell = ctx.level().getBlockState(ctx.cropPos());
        if (!cell.isAir() && !cell.canBeReplaced()) {
            return Boolean.FALSE;
        }
        return ctx.level().getBlockState(ctx.cropPos().below()).is(Blocks.FARMLAND);
    }

    @Override
    public StageOutcome applyPlant(CropWorkContext ctx, CropSpec spec) {
        if (!Boolean.TRUE.equals(needsPlant(ctx, spec))) {
            return StageOutcome.SKIPPED;
        }
        if (!WorkContainers.consume(ctx.level(), ctx.chests(), spec.seed())) {
            return StageOutcome.WAITING_MATERIAL;
        }
        ctx.level().setBlock(ctx.cropPos(), CropRules.plantState(spec), 3);
        return StageOutcome.PROCESSED;
    }

    @Override
    public Boolean needsHarvest(CropWorkContext ctx, CropSpec spec) {
        if (ctx == null || ctx.level() == null || ctx.cropPos() == null) {
            return Boolean.FALSE;
        }
        BlockState state = ctx.level().getBlockState(ctx.cropPos());
        return CropRules.isOwnPlant(spec, state) && CropAge.isMaxAge(state);
    }

    @Override
    public StageOutcome applyHarvest(CropWorkContext ctx, CropSpec spec) {
        if (!Boolean.TRUE.equals(needsHarvest(ctx, spec))) {
            return StageOutcome.SKIPPED;
        }
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos));
        WorkContainers.deposit(level, ctx.chests(), drops, pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        // 收完就地补种；土壤没变，仍是耕地。
        if (WorkContainers.consume(level, ctx.chests(), spec.seed())) {
            level.setBlock(pos, CropRules.plantState(spec), 3);
        }
        return StageOutcome.PROCESSED;
    }

    @Override
    public Boolean usesGroupedFarmScan(CropWorkContext ctx, CropSpec spec) {
        return Boolean.FALSE;
    }

    /** 不挖水渠，原来留作水渠的那一行也照常翻地 / 播种 / 收割。 */
    @Override
    public boolean allowWaterTroughCells(CropWorkContext ctx, CropSpec spec) {
        return true;
    }
}

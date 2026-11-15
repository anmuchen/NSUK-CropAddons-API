package com.nsukcropapi.crop;

import com.nsukcropapi.NsukCropApi;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.CropWork;
import com.nsukcropapi.api.CropWorkContext;
import com.nsukcropapi.api.StageOutcome;
import com.nsukcropapi.nsuk.WorkContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 作业库统一出口：把 JSON 定义映射到五个阶段的宿主钩子。
 *
 * <p>规则与乐事附属 1.1.0 完全一致（那一版已在实机验证过藤架 / 藤茎堆 / 木格架 /
 * 自定义土壤 / 普通耕地五条路径）：</p>
 * <ul>
 *   <li>藤架类（{@code isTrellisWork()}：藤架 / 木格架 / 藤茎堆）→ {@link TrellisCropWork}</li>
 *   <li>自定义土壤作物 → {@link SoilCropWork} + {@link CropHarvest}</li>
 *   <li>其它 → 返回 {@code null} 交回宿主（原版小麦、啤酒花这类行为不变）</li>
 * </ul>
 */
public final class ToolkitCropWork implements CropWork {

    public static final ToolkitCropWork INSTANCE = new ToolkitCropWork();

    private static final String STATUS_MISSING_SEED = "gui.simukraft.farmland.status.missing_seed";
    private static final String STATUS_TILL = "gui.simukraft.farmland.status.till";
    private static final String STATUS_PLANT = "gui.simukraft.farmland.status.plant";

    private ToolkitCropWork() {
    }

    @Override
    public void onTickStart(CropWorkContext ctx, CropSpec spec) {
        BoxContext.push(box(ctx));
    }

    @Override
    public void onTickEnd(CropWorkContext ctx, CropSpec spec) {
        BoxContext.pop();
    }

    @Override
    public BlockState plantState(CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        return crop == null ? null : crop.plantState();
    }

    @Override
    public boolean isMature(CropWorkContext ctx, BlockState state, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        return crop != null ? crop.isMatureFull(state) : CropWork.super.isMature(ctx, state, spec);
    }

    @Override
    public boolean isOwnPlant(BlockState state, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        return crop != null ? crop.isOwnPlant(state) : CropWork.super.isOwnPlant(state, spec);
    }

    @Override
    public Boolean needsWater(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        if (crop == null) {
            return null;
        }
        // fill_farmland 只负责铺耕地，不代表不挖水渠；只有显式 no_water / 无土 / 自定义土壤才跳过水渠。
        return crop.usesCustomSoil()
                || crop.spec().replaceSoil()
                || crop.spec().noSoil()
                || crop.spec().noWater()
                ? Boolean.FALSE : null;
    }

    @Override
    public Boolean needsTill(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.spec().noSoil()) {
            return Boolean.FALSE;
        }
        if (crop.spec().fillFarmland()) {
            return FarmlandFillWork.needsTill(level, pos);
        }
        if (crop.isCanopy()) {
            return Boolean.FALSE;
        }
        if (crop.isTrellisWork()) {
            return TrellisCropWork.needsTill(level, crop, pos);
        }
        if (crop.usesCustomSoil()) {
            return SoilCropWork.needsTill(level, crop, pos);
        }
        return null;
    }

    @Override
    public StageOutcome applyTill(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.spec().noSoil()) {
            return StageOutcome.SKIPPED;
        }
        if (crop.spec().fillFarmland()) {
            if (!FarmlandFillWork.needsTill(level, pos)) {
                return StageOutcome.SKIPPED;
            }
            return FarmlandFillWork.applyTill(level, ctx.chests(), pos)
                    ? StageOutcome.PROCESSED : StageOutcome.WAITING_MATERIAL;
        }
        boolean trellis = crop.isTrellisWork();
        if (!trellis && !crop.usesCustomSoil()) {
            return null;
        }
        boolean needed = trellis
                ? TrellisCropWork.needsTill(level, crop, pos)
                : SoilCropWork.needsTill(level, crop, pos);
        if (!needed) {
            return StageOutcome.SKIPPED;
        }
        boolean acted = trellis
                ? TrellisCropWork.applyTill(level, crop, ctx.chests(), pos)
                : SoilCropWork.applyTill(level, crop, ctx.chests(), pos);
        return acted ? StageOutcome.PROCESSED : StageOutcome.WAITING_MATERIAL;
    }

    @Override
    public Boolean needsPlant(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.spec().noSoil()) {
            BlockState cell = level.getBlockState(pos);
            if (!cell.isAir() && !cell.canBeReplaced()) {
                return Boolean.FALSE;
            }
            return crop.plantState().canSurvive(level, pos) ? Boolean.TRUE : Boolean.FALSE;
        }
        // fill_farmland：土壤由本策略铺成耕地，宿主 needsPlantWork 会把水渠行整行排除，
        // 只放开扫描范围而不接管播种，水渠行仍会永久空一列，所以这里自己判定。
        if (crop.spec().fillFarmland()) {
            BlockState cell = level.getBlockState(pos);
            if (!cell.isAir() && !cell.canBeReplaced()) {
                return Boolean.FALSE;
            }
            return level.getBlockState(pos.below()).is(Blocks.FARMLAND) ? Boolean.TRUE : Boolean.FALSE;
        }
        if (crop.isCanopy()) {
            return CanopyCropWork.needsPlant(level, crop, pos);
        }
        if (crop.isTrellisWork()) {
            return TrellisCropWork.needsPlant(level, crop, pos);
        }
        if (crop.usesCustomSoil()) {
            return SoilCropWork.needsPlant(level, crop, pos);
        }
        return null;
    }

    @Override
    public StageOutcome applyPlant(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.spec().noSoil()) {
            if (!Boolean.TRUE.equals(needsPlant(ctx, spec))) {
                return StageOutcome.SKIPPED;
            }
            if (!WorkContainers.consume(level, ctx.chests(), crop.seed())) {
                return StageOutcome.WAITING_MATERIAL;
            }
            level.setBlock(pos, crop.plantState(), 3);
            return StageOutcome.PROCESSED;
        }
        if (crop.spec().fillFarmland()) {
            if (!Boolean.TRUE.equals(needsPlant(ctx, spec))) {
                return StageOutcome.SKIPPED;
            }
            if (!WorkContainers.consume(level, ctx.chests(), crop.seed())) {
                return StageOutcome.WAITING_MATERIAL;
            }
            level.setBlock(pos, crop.plantState(), 3);
            return StageOutcome.PROCESSED;
        }
        if (crop.isCanopy()) {
            if (!CanopyCropWork.needsPlant(level, crop, pos)) {
                return StageOutcome.SKIPPED;
            }
            return CanopyCropWork.applyPlant(level, crop, ctx.chests(), pos)
                    ? StageOutcome.PROCESSED : StageOutcome.WAITING_MATERIAL;
        }
        boolean trellis = crop.isTrellisWork();
        if (!trellis && !crop.usesCustomSoil()) {
            return null;
        }
        boolean needed = trellis
                ? TrellisCropWork.needsPlant(level, crop, pos)
                : SoilCropWork.needsPlant(level, crop, pos);
        if (!needed) {
            return StageOutcome.SKIPPED;
        }
        boolean acted = trellis
                ? TrellisCropWork.applyPlant(level, crop, ctx.chests(), pos)
                : SoilCropWork.applyPlant(level, crop, ctx.chests(), pos);
        return acted ? StageOutcome.PROCESSED : StageOutcome.WAITING_MATERIAL;
    }

    @Override
    public Boolean needsBonemeal(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.isCanopy()) {
            return CanopyCropWork.needsBonemeal(level, crop, pos);
        }
        if (!crop.isTrellisWork()) {
            return null;
        }
        return TrellisCropWork.needsBonemeal(level, crop, pos)
                && WorkContainers.has(level, ctx.chests(), Items.BONE_MEAL);
    }

    @Override
    public StageOutcome applyBonemeal(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.isCanopy()) {
            return CanopyCropWork.applyBonemeal(level, crop, pos)
                    ? StageOutcome.PROCESSED : StageOutcome.SKIPPED;
        }
        if (!crop.isTrellisWork()) {
            return null;
        }
        if (!TrellisCropWork.needsBonemeal(level, crop, pos)) {
            return StageOutcome.SKIPPED;
        }
        if (!WorkContainers.consume(level, ctx.chests(), Items.BONE_MEAL)) {
            return StageOutcome.WAITING_MATERIAL;
        }
        TrellisCropWork.applyBonemeal(level, crop, pos);
        return StageOutcome.PROCESSED;
    }

    @Override
    public Boolean needsHarvest(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.isCanopy()) {
            return CanopyCropWork.isReadyToHarvest(level, crop, pos);
        }
        if (crop.isTrellisWork()) {
            return TrellisCropWork.isReadyToHarvest(level, crop, pos);
        }
        return crop.isStem() ? null : CropHarvest.isReady(level, crop, pos);
    }

    @Override
    public StageOutcome applyHarvest(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        ServerLevel level = ctx.level();
        BlockPos pos = ctx.cropPos();
        if (crop == null || level == null || pos == null) {
            return null;
        }
        if (crop.isCanopy()) {
            if (!CanopyCropWork.isReadyToHarvest(level, crop, pos)) {
                return StageOutcome.SKIPPED;
            }
            CanopyCropWork.harvest(level, crop, ctx.chests(), pos);
            return StageOutcome.PROCESSED;
        }
        if (crop.isTrellisWork()) {
            if (!TrellisCropWork.isReadyToHarvest(level, crop, pos)) {
                return StageOutcome.SKIPPED;
            }
            TrellisCropWork.harvest(level, crop, ctx.chests(), pos);
            return StageOutcome.PROCESSED;
        }
        if (crop.isStem()) {
            return null;
        }
        if (!CropHarvest.isReady(level, crop, pos)) {
            return StageOutcome.SKIPPED;
        }
        CropHarvest.harvest(level, crop, ctx.chests(), pos);
        CropHarvest.replantIfNeeded(level, crop, ctx.chests(), pos);
        return StageOutcome.PROCESSED;
    }

    @Override
    public Boolean seedAvailable(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        if (crop == null || ctx.level() == null) {
            return null;
        }
        if (crop.isCanopy()) {
            return CanopyCropWork.needsSupport(ctx.level(), crop) ? Boolean.TRUE : null;
        }
        if (!crop.isTrellisWork()) {
            return null;
        }
        return TrellisCropWork.needsSeedlessPlantWork(ctx.level(), crop) ? Boolean.TRUE : null;
    }

    @Override
    public Boolean usesGroupedFarmScan(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        if (crop == null) {
            return null;
        }
        return crop.usesCustomSoil()
                || crop.spec().fillFarmland()
                || crop.spec().replaceSoil()
                || crop.spec().noSoil()
                ? Boolean.FALSE : null;
    }

    @Override
    public boolean allowWaterTroughCells(CropWorkContext ctx, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        // 只有显式 allow_water_trough 的作物（马勃菌这类完全不需要土壤的）才接管水渠占位行。
        // 普通耕地作物必须把水渠行留给宿主：否则我们会先把水渠行铺成耕地、宿主下一 tick
        // 又把它挖回水，雇农就会永远卡在这一列，看起来“只挖水渠、不铺泥土”。
        return crop != null && crop.spec().allowWaterTrough();
    }

    @Override
    public Boolean usesTrellisWork(CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        return crop == null ? null : crop.isTrellisWork();
    }

    @Override
    public boolean hiddenFromUi(CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        return crop != null && crop.spec().hidden();
    }

    @Override
    public Component statusLabel(CropWorkContext ctx, String statusKey, CropSpec spec) {
        ResolvedCrop crop = crop(spec);
        if (crop == null || !isSupplyStatus(statusKey)) {
            return null;
        }
        ServerLevel level = ctx.level();
        if (level == null) {
            return null;
        }
        if (crop.spec().fillFarmland() && STATUS_MISSING_SEED.equals(statusKey)) {
            BlockPos pos = ctx.cropPos();
            if (pos != null && FarmlandFillWork.needsTill(level, pos)
                    && FarmlandFillWork.findSoilItem(level, ctx.chests()) == null) {
                return Component.translatable("gui.nsukcropapi.waiting_soil",
                        new ItemStack(Items.DIRT).getHoverName());
            }
        }
        if (!crop.usesCustomSoil() && !crop.isCanopy()) {
            return null;
        }
        Item item = crop.isCanopy()
                ? CanopyCropWork.statusItemForPhase(level, crop, box(ctx), statusKey)
                : TrellisCropWork.statusItemForPhase(level, crop, box(ctx), statusKey);
        Component itemName = item != null && item != Items.AIR
                ? item.getName(new ItemStack(item))
                : Component.translatable(crop.statusTranslationKey());
        boolean missingSeed = STATUS_MISSING_SEED.equals(statusKey);
        boolean till = STATUS_TILL.equals(statusKey);
        boolean soilItem = item != null && item != Items.AIR && crop.soilItems().contains(item);
        boolean seedItem = item != null && item != Items.AIR && item == crop.seed();
        String key;
        if (missingSeed && soilItem) {
            key = "gui.nsukcropapi.waiting_soil";
        } else if (missingSeed && seedItem) {
            key = statusKey;
        } else if (missingSeed) {
            key = "gui.nsukcropapi.trellis.waiting_item";
        } else if (till) {
            key = "gui.nsukcropapi.trellis.placing_soil";
        } else if (crop.isTrellisWork() && !seedItem) {
            key = "gui.nsukcropapi.trellis.placing_item";
        } else {
            key = statusKey;
        }
        return Component.translatable(key, itemName);
    }

    private static boolean isSupplyStatus(String statusKey) {
        return STATUS_MISSING_SEED.equals(statusKey)
                || STATUS_TILL.equals(statusKey)
                || STATUS_PLANT.equals(statusKey);
    }

    private static ResolvedCrop crop(CropSpec spec) {
        if (spec == null || spec.id() == null) {
            return null;
        }
        return CropCatalog.resolve(spec.id()).orElse(null);
    }

    private static common.cn.nsimu.simukraft.farmland.FarmlandBoxData box(CropWorkContext ctx) {
        return ctx != null && ctx.data() instanceof common.cn.nsimu.simukraft.farmland.FarmlandBoxData data ? data : null;
    }
}

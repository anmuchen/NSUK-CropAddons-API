package com.nsukcropapi.nsuk;

import com.nsukcropapi.NsukCropApi;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.CropWork;
import com.nsukcropapi.api.CropWorkContext;
import com.nsukcropapi.api.StageOutcome;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxData;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxService;
import common.cn.nsimu.simukraft.farmland.FarmlandPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 农田盒作业引擎（API 侧的接管点）。
 *
 * <p>负责把宿主 {@code FarmlandFarmingService} 的五个作业阶段
 * （DIG_WATER / TILL / PLANT / BONEMEAL / HARVEST）转交给当前作物注册的 {@link CropWork}：
 * 先问 {@code needsXxx}，返回 {@code null} 就原样回落宿主逻辑，返回真则调用 {@code applyXxx}。</p>
 *
 * <p>这里统一兜两条历史坑：</p>
 * <ul>
 *   <li><b>水槽行</b>：除 DIG_WATER 外，水槽格一律回答"不需要"。宿主 {@code findTarget}
 *       只要发现一格需要翻地就会一直返回该目标，若让作物接管了水槽格，雇农会永久冻结在翻地阶段。</li>
 *   <li><b>播种范围</b>：TILL / PLANT 先过 {@link CropWork#canPlantAt}，让作物能把地块里
 *       不该种的那部分格子让出去，而不是硬塞一个够不着的目标。</li>
 * </ul>
 */
public final class FarmlandFarmingEngine {

    private static final int WATER_STRIDE = 4;
    private static final int WATER_TROUGH_INDEX = 3;

    private static final Set<String> APPLY_MISSING_WARNED = ConcurrentHashMap.newKeySet();

    private FarmlandFarmingEngine() {
    }

    /** 五个作业阶段，与宿主 {@code FarmlandWorkPhase} 一一对应。 */
    private enum Stage {
        WATER, TILL, PLANT, BONEMEAL, HARVEST
    }

    /** decide() 的结论：是否接管、这一格现在要不要做。 */
    private record Decision(boolean takenOver, boolean needed) {
        static final Decision NOT_TAKEN = new Decision(false, false);
    }

    /** 组装当前 tick 的作业上下文；任何一步失败都退回最小上下文。 */
    public static CropWorkContext context(ServerLevel level, FarmlandBoxData data) {
        try {
            BlockPos boxPos = data.boxPos();
            List<BlockPos> chests = FarmlandBoxService.resolveAdjacentChests(level, boxPos);
            return new CropWorkContext(level, boxPos, null,
                    chests == null ? List.of() : new ArrayList<>(chests), data, null);
        } catch (Throwable t) {
            return CropWorkContext.minimal(level, null);
        }
    }

    /**
     * 组装某一格、某一阶段的上下文：补齐格子坐标与脚下土壤，箱子优先用宿主本次调用给出的列表。
     */
    public static CropWorkContext stage(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        CropWorkContext base = CropWorkContextHolder.current();
        FarmlandBoxData boxData = data;
        if (boxData == null && base != null && base.data() instanceof FarmlandBoxData fromHolder) {
            boxData = fromHolder;
        }
        List<BlockPos> containers;
        if (chests != null && !chests.isEmpty()) {
            containers = new ArrayList<>(chests);
        } else if (base != null && !base.chests().isEmpty()) {
            containers = base.chests();
        } else if (level != null && boxData != null) {
            List<BlockPos> resolved = FarmlandBoxService.resolveAdjacentChests(level, boxData.boxPos());
            containers = resolved == null ? List.of() : new ArrayList<>(resolved);
        } else {
            containers = List.of();
        }
        BlockPos boxPos = boxData != null ? boxData.boxPos() : base != null ? base.boxPos() : null;
        BlockState soil = null;
        if (level != null && cropPos != null && level.isLoaded(cropPos)) {
            soil = level.getBlockState(cropPos.below());
        }
        return new CropWorkContext(level, boxPos, cropPos, containers, boxData, soil);
    }

    /** 水槽行判定：与宿主 {@code FarmlandWorkGeometry.isWaterTrough} 完全一致的公式。 */
    public static boolean isWaterTrough(FarmlandBoxData data, BlockPos cropPos) {
        if (data == null || cropPos == null) {
            return false;
        }
        FarmlandPlot plot = data.plot();
        if (plot == null || plot.min() == null) {
            return false;
        }
        return Math.floorMod(cropPos.getZ() - plot.min().getZ(), WATER_STRIDE) == WATER_TROUGH_INDEX;
    }

    // ------------------------------------------------------------------
    // 五个阶段：needs（是否需要做）/ apply（真正去做）
    // 返回 null 表示"没接管，交回宿主"
    // ------------------------------------------------------------------

    public static Boolean needsWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        return needs(Stage.WATER, level, data, chests, cropPos);
    }

    public static StageOutcome applyWater(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        return apply(Stage.WATER, level, data, chests, cropPos);
    }

    public static Boolean needsTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        return needs(Stage.TILL, level, data, List.of(), cropPos);
    }

    public static StageOutcome applyTill(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        return apply(Stage.TILL, level, data, List.of(), cropPos);
    }

    public static Boolean needsPlant(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        return needs(Stage.PLANT, level, data, List.of(), cropPos);
    }

    public static StageOutcome applyPlant(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        return apply(Stage.PLANT, level, data, chests, cropPos);
    }

    public static Boolean needsBonemeal(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        return needs(Stage.BONEMEAL, level, data, chests, cropPos);
    }

    public static StageOutcome applyBonemeal(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        return apply(Stage.BONEMEAL, level, data, chests, cropPos);
    }

    public static Boolean needsHarvest(ServerLevel level, FarmlandBoxData data, BlockPos cropPos) {
        return needs(Stage.HARVEST, level, data, List.of(), cropPos);
    }

    public static StageOutcome applyHarvest(ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        return apply(Stage.HARVEST, level, data, chests, cropPos);
    }

    /** 箱子里的种子够不够（决定宿主是否扫描 PLANT 阶段）。 */
    public static Boolean seedAvailable(ServerLevel level, List<BlockPos> chests) {
        CropSpec spec = CropContext.current();
        if (spec == null || level == null) {
            return null;
        }
        CropWorkContext base = CropWorkContextHolder.current();
        FarmlandBoxData data = base != null && base.data() instanceof FarmlandBoxData boxData ? boxData : null;
        try {
            return spec.workOrDefault().seedAvailable(stage(level, data, chests, null), spec);
        } catch (Throwable t) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的种子判定抛异常，交回宿主", spec.id(), t);
            return null;
        }
    }

    /** 雇农头顶文案：返回序列化好的 JSON，{@code null} 表示用宿主原文案。 */
    public static String statusLabel(ServerLevel level, String statusKey) {
        CropSpec spec = CropContext.current();
        if (spec == null || level == null) {
            return null;
        }
        CropWorkContext ctx = CropWorkContextHolder.current();
        if (ctx == null) {
            ctx = CropWorkContext.minimal(level, null);
        }
        try {
            Component label = spec.workOrDefault().statusLabel(ctx, statusKey, spec);
            return label == null ? null : Component.Serializer.toJson(label, level.registryAccess());
        } catch (Throwable t) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的状态文案抛异常，交回宿主", spec.id(), t);
            return null;
        }
    }

    /** 是否沿用宿主的分组扫描；{@code null} 表示交回宿主。 */
    public static Boolean usesGroupedFarmScan() {
        CropSpec spec = CropContext.current();
        if (spec == null) {
            return null;
        }
        CropWorkContext ctx = CropWorkContextHolder.current();
        try {
            return spec.workOrDefault().usesGroupedFarmScan(
                    ctx != null ? ctx : CropWorkContext.minimal(null, null), spec);
        } catch (Throwable t) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的扫描模式判定抛异常，交回宿主", spec.id(), t);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 内部：判定与执行
    // ------------------------------------------------------------------

    private static Boolean needs(Stage stage, ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        Decision decision = decide(stage, level, data, chests, cropPos);
        if (!decision.takenOver()) {
            return null;
        }
        return decision.needed();
    }

    private static StageOutcome apply(Stage stage, ServerLevel level, FarmlandBoxData data, List<BlockPos> chests, BlockPos cropPos) {
        Decision decision = decide(stage, level, data, chests, cropPos);
        if (!decision.takenOver()) {
            return null;
        }
        if (!decision.needed()) {
            return StageOutcome.SKIPPED;
        }
        CropSpec spec = CropContext.current();
        if (spec == null) {
            return null;
        }
        CropWork work = spec.workOrDefault();
        CropWorkContext ctx = stage(level, data, chests, cropPos);
        StageOutcome outcome;
        try {
            outcome = switch (stage) {
                case WATER -> work.applyWater(ctx, spec);
                case TILL -> work.applyTill(ctx, spec);
                case PLANT -> work.applyPlant(ctx, spec);
                case BONEMEAL -> work.applyBonemeal(ctx, spec);
                case HARVEST -> work.applyHarvest(ctx, spec);
            };
        } catch (Throwable t) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的 {} 阶段执行抛异常，本 tick 跳过", spec.id(), stage, t);
            return StageOutcome.SKIPPED;
        }
        if (outcome == null) {
            if (APPLY_MISSING_WARNED.add(spec.id() + "@" + stage)) {
                NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 接管了 {} 阶段的判定，却没有实现对应的 apply 方法；"
                        + "这一格会反复被选中，请补上实现", spec.id(), stage);
            }
            return StageOutcome.SKIPPED;
        }
        return outcome;
    }

    private static Decision decide(Stage stage, ServerLevel level, FarmlandBoxData data,
                                   List<BlockPos> chests, BlockPos cropPos) {
        CropSpec spec = CropContext.current();
        if (spec == null || level == null || data == null || cropPos == null) {
            return Decision.NOT_TAKEN;
        }
        CropWork work = spec.workOrDefault();
        CropWorkContext ctx;
        Boolean verdict;
        try {
            ctx = stage(level, data, chests, cropPos);
            verdict = switch (stage) {
                case WATER -> work.needsWater(ctx, spec);
                case TILL -> work.needsTill(ctx, spec);
                case PLANT -> work.needsPlant(ctx, spec);
                case BONEMEAL -> work.needsBonemeal(ctx, spec);
                case HARVEST -> work.needsHarvest(ctx, spec);
            };
        } catch (Throwable t) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的 {} 阶段判定抛异常，本 tick 交回宿主", spec.id(), stage, t);
            return Decision.NOT_TAKEN;
        }
        if (verdict == null) {
            return Decision.NOT_TAKEN;
        }
        if (!verdict) {
            return new Decision(true, false);
        }
        // 水槽行只允许 DIG_WATER 阶段接管；不需要土壤的作物可以在 JSON 里显式放开。
        if (stage != Stage.WATER && isWaterTrough(data, cropPos)
                && !work.allowWaterTroughCells(ctx, spec)) {
            return new Decision(true, false);
        }
        // 翻地/播种先问作物这一格能不能种
        if ((stage == Stage.TILL || stage == Stage.PLANT) && !work.canPlantAt(ctx, ctx.soil(), spec)) {
            return new Decision(true, false);
        }
        return new Decision(true, true);
    }
}

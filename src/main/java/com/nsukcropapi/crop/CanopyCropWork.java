package com.nsukcropapi.crop;

import common.cn.nsimu.simukraft.farmland.FarmlandBoxData;
import common.cn.nsimu.simukraft.farmland.FarmlandPlot;
import common.cn.nsimu.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 雨棚布局（烟火凡人心的番茄 / 黄瓜 / 葡萄等）：格子四角立柱 + 顶盖，作物种在角柱上。
 *
 * <p>结构：{@code cell} 底土 · {@code corner.above(1..2)} 两格角柱 · {@code cell.above(3)} 顶盖，
 * 种子种在角柱顶端（角柱自带 {@code distance}/{@code bottom} 方块状态，由原版渲染连线）。
 * 生长走原版随机刻；成熟判定优先看 {@code stage} 属性，退回年龄最大值；
 * 收割反射调用作物方块的 {@code harvest(BlockState, Level, BlockPos)}（烟火凡人心自带）。</p>
 */
public final class CanopyCropWork {

    public static final int ROOF_HEIGHT = 3;
    private static final Map<Class<?>, Method> HARVEST_METHODS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> UNHARVESTABLE = ConcurrentHashMap.newKeySet();

    private CanopyCropWork() {
    }

    public static boolean needsTill(ServerLevel level, ResolvedCrop crop, BlockPos cropPos) {
        return false;
    }

    public static boolean applyTill(ServerLevel level, ResolvedCrop crop, List<BlockPos> chests, BlockPos cropPos) {
        return false;
    }

    /** 还有没有立柱 / 顶盖要搭（供"是否无视种子扫描 PLANT"判断用）。 */
    public static boolean needsSupport(ServerLevel level, ResolvedCrop crop) {
        Structure structure = structure(crop);
        return structure != null
                && (missingRoof(level, crop, structure) != null || missingPost(level, crop, structure) != null);
    }

    public static boolean needsPlant(ServerLevel level, ResolvedCrop crop, BlockPos cropPos) {
        Structure structure = structure(crop);
        return structure == null
                ? false
                : missingRoof(level, crop, structure) != null
                || missingPost(level, crop, structure) != null
                || missingSeed(level, crop, structure) != null;
    }

    public static boolean applyPlant(ServerLevel level, ResolvedCrop crop, List<BlockPos> chests, BlockPos cropPos) {
        Structure structure = structure(crop);
        if (structure == null) {
            return false;
        }
        BlockPos roof = missingRoof(level, crop, structure);
        if (roof != null) {
            return placeSupport(level, crop, chests, roof, 0, false);
        }
        BlockPos post = missingPost(level, crop, structure);
        if (post != null) {
            boolean bottom = post.getY() == structure.baseY() + 1;
            return placeSupport(level, crop, chests, post, bottom ? 0 : 1, bottom);
        }
        BlockPos seed = missingSeed(level, crop, structure);
        if (seed != null && WorkContainerService.consumeItem(level, chests, crop.seed())) {
            level.setBlock(seed, crop.plantBlock().defaultBlockState(), 3);
            return true;
        }
        return false;
    }

    public static boolean needsBonemeal(ServerLevel level, ResolvedCrop crop, BlockPos cropPos) {
        return findRipening(level, crop) != null;
    }

    public static boolean applyBonemeal(ServerLevel level, ResolvedCrop crop, BlockPos cropPos) {
        BlockPos pos = findRipening(level, crop);
        if (pos == null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, pos, state)) {
            growable.performBonemeal(level, level.getRandom(), pos, state);
            return true;
        }
        return false;
    }

    public static boolean isReadyToHarvest(ServerLevel level, ResolvedCrop crop, BlockPos cropPos) {
        return findRipe(level, crop) != null;
    }

    public static void harvest(ServerLevel level, ResolvedCrop crop, List<BlockPos> chests, BlockPos cropPos) {
        Structure structure = structure(crop);
        if (structure != null) {
            for (BlockPos pos : structure.all()) {
                harvestAt(level, crop, chests, pos);
            }
        }
    }

    public static void harvestAt(ServerLevel level, ResolvedCrop crop, List<BlockPos> chests, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(crop.plantBlock()) && isRipe(state)) {
            AABB area = new AABB(pos).inflate(1.5);
            Set<UUID> before = itemIds(level, area);
            invokeHarvest(state, level, pos);
            collectNewItems(level, chests, area, before);
        }
    }

    /** 雇农头顶文案：还在搭结构时报支撑材料，否则报种子。 */
    public static Item statusItemForPhase(ServerLevel level, ResolvedCrop crop, FarmlandBoxData data, String phaseKey) {
        if (crop == null) {
            return Items.AIR;
        }
        if (phaseKey != null && phaseKey.endsWith(".plant")) {
            Structure structure = structure(crop);
            boolean needsSupport = structure != null
                    && (missingRoof(level, crop, structure) != null || missingPost(level, crop, structure) != null);
            return needsSupport ? supportItem(crop) : crop.seed();
        }
        return crop.seed() != Items.AIR ? crop.seed() : supportItem(crop);
    }

    private static Structure structure(ResolvedCrop crop) {
        if (crop == null || !crop.isCanopy() || crop.isMissing()) {
            return null;
        }
        FarmlandBoxData data = BoxContext.currentBox().orElse(null);
        FarmlandPlot plot = data == null ? null : data.plot();
        if (plot == null || plot.cellCount() <= 0) {
            return null;
        }
        List<BlockPos> cells = new ArrayList<>();
        int count = plot.cellCount();
        for (int index = 0; index < count; index++) {
            cells.add(plot.cellAt(index).immutable());
        }
        return new Structure(cells, plot);
    }

    private static BlockPos missingRoof(ServerLevel level, ResolvedCrop crop, Structure structure) {
        for (BlockPos cell : structure.cells()) {
            BlockPos roof = cell.above(ROOF_HEIGHT);
            if (!level.isLoaded(roof)) {
                return null;
            }
            BlockState state = level.getBlockState(roof);
            if (!state.is(crop.supportBlock()) && !state.is(crop.plantBlock())) {
                if (!state.isAir() && !state.canBeReplaced()) {
                    return null;
                }
                return roof;
            }
        }
        return null;
    }

    private static BlockPos missingPost(ServerLevel level, ResolvedCrop crop, Structure structure) {
        for (BlockPos corner : structure.corners()) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos pos = corner.above(dy);
                if (!level.isLoaded(pos)) {
                    return null;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(crop.supportBlock()) && !state.is(crop.plantBlock())) {
                    if (!state.isAir() && !state.canBeReplaced()) {
                        return null;
                    }
                    return pos;
                }
            }
        }
        return null;
    }

    /** 角柱顶端正等着播种（原版里角柱本身就是"可种植的支撑"）。 */
    private static BlockPos missingSeed(ServerLevel level, ResolvedCrop crop, Structure structure) {
        for (BlockPos corner : structure.corners()) {
            BlockPos pos = corner.above(1);
            if (!level.isLoaded(pos)) {
                return null;
            }
            if (level.getBlockState(pos).is(crop.supportBlock())) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos findRipening(ServerLevel level, ResolvedCrop crop) {
        Structure structure = structure(crop);
        if (structure == null) {
            return null;
        }
        for (BlockPos pos : structure.all()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(crop.plantBlock()) && !isRipe(state)) {
                return pos;
            }
        }
        return null;
    }

    private static BlockPos findRipe(ServerLevel level, ResolvedCrop crop) {
        Structure structure = structure(crop);
        if (structure == null) {
            return null;
        }
        for (BlockPos pos : structure.all()) {
            BlockState state = level.getBlockState(pos);
            if (state.is(crop.plantBlock()) && isRipe(state)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean placeSupport(ServerLevel level, ResolvedCrop crop, List<BlockPos> chests,
                                        BlockPos pos, int distance, boolean bottom) {
        Item item = supportItem(crop);
        if (item == Items.AIR || !WorkContainerService.consumeItem(level, chests, item)) {
            return false;
        }
        BlockState state = crop.supportBlock().defaultBlockState();
        state = withInt(state, "distance", distance);
        state = withFlag(state, "bottom", bottom);
        BlockState previous = level.getBlockState(pos);
        if (!previous.isAir() && previous.canBeReplaced()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, state, 3);
        return true;
    }

    private static Item supportItem(ResolvedCrop crop) {
        Block support = crop.supportBlock();
        if (support == null || support == Blocks.AIR) {
            return Items.AIR;
        }
        Item item = support.asItem();
        return item instanceof BlockItem ? item : Items.AIR;
    }

    private static BlockState withInt(BlockState state, String name, int value) {
        for (Property<?> property : state.getProperties()) {
            if (name.equals(property.getName()) && property instanceof IntegerProperty integer) {
                int clamped = Math.max(0, Math.min(value, maxOf(integer)));
                return state.setValue(integer, clamped);
            }
        }
        return state;
    }

    private static int maxOf(IntegerProperty property) {
        int highest = 0;
        for (int value : property.getPossibleValues()) {
            highest = Math.max(highest, value);
        }
        return highest;
    }

    private static BlockState withFlag(BlockState state, String name, boolean value) {
        for (Property<?> property : state.getProperties()) {
            if (name.equals(property.getName()) && property instanceof BooleanProperty flag) {
                return state.setValue(flag, value);
            }
        }
        return state;
    }

    /** 成熟判定：优先 {@code stage} 属性，退回年龄到顶。 */
    public static boolean isRipe(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if ("stage".equals(property.getName()) && property instanceof IntegerProperty stage) {
                return state.getValue(stage) >= maxOf(stage);
            }
        }
        return CropAge.isMaxAge(state);
    }

    private static void invokeHarvest(BlockState state, ServerLevel level, BlockPos pos) {
        Block block = state.getBlock();
        Class<?> type = block.getClass();
        if (UNHARVESTABLE.contains(type)) {
            return;
        }
        Method method = HARVEST_METHODS.get(type);
        if (method == null) {
            try {
                method = type.getMethod("harvest", BlockState.class, net.minecraft.world.level.Level.class, BlockPos.class);
                method.setAccessible(true);
                HARVEST_METHODS.put(type, method);
            } catch (Throwable throwable) {
                UNHARVESTABLE.add(type);
                return;
            }
        }
        try {
            method.invoke(block, state, level, pos);
        } catch (Throwable throwable) {
            com.nsukcropapi.NsukCropApi.LOGGER.warn("[nsukcropapi] 雨棚作物收割失败 @{}", pos, throwable);
        }
    }

    private static Set<UUID> itemIds(ServerLevel level, AABB area) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ids.add(entity.getUUID());
        }
        return ids;
    }

    private static void collectNewItems(ServerLevel level, List<BlockPos> chests, AABB area, Set<UUID> before) {
        for (ItemEntity entity : List.copyOf(level.getEntitiesOfClass(ItemEntity.class, area))) {
            if (entity.isAlive() && !before.contains(entity.getUUID())) {
                ItemStack leftover = WorkContainerService.insertIntoAny(level, chests, entity.getItem());
                if (leftover.isEmpty()) {
                    entity.discard();
                } else {
                    entity.setItem(leftover);
                }
            }
        }
    }

    /** 结构缓存：格子列表 + 地块（角柱 = 地块四角）。 */
    private record Structure(List<BlockPos> cells, FarmlandPlot plot) {

        List<BlockPos> corners() {
            List<BlockPos> corners = new ArrayList<>();
            if (plot == null || plot.min() == null || plot.max() == null) {
                return corners;
            }
            BlockPos min = plot.min();
            BlockPos max = plot.max();
            corners.add(min.immutable());
            corners.add(new BlockPos(max.getX(), min.getY(), min.getZ()));
            corners.add(new BlockPos(min.getX(), min.getY(), max.getZ()));
            corners.add(new BlockPos(max.getX(), min.getY(), max.getZ()));
            return corners;
        }

        /** 所有可能长作物的位置：角柱及其上一层。 */
        List<BlockPos> all() {
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos corner : corners()) {
                out.add(corner.above(1));
                out.add(corner.above(2));
            }
            return out;
        }

        int baseY() {
            return plot != null && plot.min() != null ? plot.min().getY() : 0;
        }
    }
}

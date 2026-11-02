package com.nsukcropapi.crop;

import com.nsukcropapi.nsuk.WorkContainers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 「普通耕地作物自动铺地」：下方不是耕地时，优先把泥土/草方块翻成耕地；
 * 下方是石头、沙子等其它方块时，从箱子取一份泥土，把原方块打掉收进箱子，再铺成湿润耕地。
 *
 * <p>对应 JSON 的 {@code fill_farmland: true}。这样即使农田盒搭在石头平台上，
 * 只要箱子里备着泥土，雇农也会把整片地铺满再播种，而不是只在已经有泥土的格子上种。</p>
 */
public final class FarmlandFillWork {

    private FarmlandFillWork() {
    }

    public static boolean needsTill(ServerLevel level, BlockPos cropPos) {
        if (level == null || cropPos == null || !level.isLoaded(cropPos)) {
            return false;
        }
        BlockState soil = level.getBlockState(cropPos.below());
        if (soil.is(Blocks.FARMLAND)) {
            // 已经有耕地：顺手把湿度补满，避免 no_water 作物（生咖啡豆）的耕地干枯退化成泥土。
            if (soil.getValue(FarmBlock.MOISTURE) < 7) {
                level.setBlock(cropPos.below(),
                        Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
            }
            return false;
        }
        // 容器 / 基岩 / 流体不动，避免把玩家机器或水渠拆了；这些格子也不该反复被选中。
        return !soil.hasBlockEntity()
                && !soil.is(Blocks.BEDROCK)
                && soil.getFluidState().isEmpty();
    }

    public static boolean applyTill(ServerLevel level, List<BlockPos> chests, BlockPos cropPos) {
        if (level == null || cropPos == null || !level.isLoaded(cropPos)) {
            return false;
        }
        BlockPos soilPos = cropPos.below();
        BlockState soil = level.getBlockState(soilPos);
        if (soil.is(Blocks.FARMLAND)) {
            return true;
        }

        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7);

        // 泥土类直接翻地，不消耗箱子里的泥土。
        if (isLooseSoil(soil)) {
            level.setBlock(soilPos, farmland, 3);
            return true;
        }

        // 容器 / 基岩 / 流体不动，避免把玩家机器拆了。
        if (soil.hasBlockEntity() || soil.is(Blocks.BEDROCK) || !soil.getFluidState().isEmpty()) {
            return false;
        }

        Item soilItem = findSoilItem(level, chests);
        if (soilItem == null || !WorkContainers.consume(level, chests, soilItem)) {
            return false;
        }
        if (!soil.isAir()) {
            List<ItemStack> drops = Block.getDrops(soil, level, soilPos, level.getBlockEntity(soilPos));
            level.setBlock(soilPos, Blocks.AIR.defaultBlockState(), 3);
            WorkContainers.deposit(level, chests, drops, soilPos);
        }
        level.setBlock(soilPos, farmland, 3);
        return true;
    }

    /** 箱子里可用的「铺地材料」；优先泥土，也接受草方块、砂土等泥土系方块。 */
    public static Item findSoilItem(ServerLevel level, List<BlockPos> chests) {
        if (level == null || chests == null || chests.isEmpty()) {
            return null;
        }
        for (Item item : SOIL_ITEMS) {
            if (WorkContainers.has(level, chests, item)) {
                return item;
            }
        }
        return null;
    }

    private static final List<Item> SOIL_ITEMS = List.of(
            Items.DIRT,
            Items.GRASS_BLOCK,
            Items.COARSE_DIRT,
            Items.ROOTED_DIRT,
            Items.PODZOL,
            Items.MYCELIUM,
            Items.DIRT_PATH,
            Items.FARMLAND
    );

    private static boolean isLooseSoil(BlockState soil) {
        return soil.is(Blocks.DIRT)
                || soil.is(Blocks.GRASS_BLOCK)
                || soil.is(Blocks.COARSE_DIRT)
                || soil.is(Blocks.PODZOL)
                || soil.is(Blocks.ROOTED_DIRT)
                || soil.is(Blocks.MYCELIUM)
                || soil.is(Blocks.DIRT_PATH);
    }
}

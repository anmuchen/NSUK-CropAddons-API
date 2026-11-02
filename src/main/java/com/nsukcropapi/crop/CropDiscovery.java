package com.nsukcropapi.crop;

import com.nsukcropapi.NsukCropApi;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.FarmlandCropWork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 自动发现：把已加载模组里"长得像耕地作物"的 {@link CropBlock} 也接进农田盒。
 *
 * <p>只负责补漏——附属 JSON 里写过的作物优先，这里不会覆盖。
 * 因为拿不到产物的准确定义，自动发现的作物统一用种子作图标、种子名作显示名，
 * 作业策略走 {@link FarmlandCropWork}（自动铺地、不挖水渠）。</p>
 *
 * <p>由 {@code autoDiscovery} 开关控制，默认关闭：不同模组的 {@code CropBlock} 差异很大，
 * 盲目全收容易把装饰性方块当成作物。开启后如果结果不对，直接关掉即可，不会污染存档。</p>
 */
public final class CropDiscovery {

    /** 作物 id 上限，与 {@code CropSpec.MAX_ID_LENGTH} 一致。 */
    private static final int MAX_ID_LENGTH = 32;

    private CropDiscovery() {
    }

    public static int discover() {
        Method seedAccessor = seedAccessor();
        int added = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof CropBlock cropBlock)) {
                continue;
            }
            if (block == Blocks.AIR || shouldSkip(block)) {
                continue;
            }
            ResourceLocation plantId = BuiltInRegistries.BLOCK.getKey(block);
            if (plantId == null) {
                continue;
            }
            Item seed = resolveSeed(block, seedAccessor);
            if (seed == null || seed == Items.AIR) {
                continue;
            }
            // 附属 JSON 已经定义过的种子，不再重复注册。
            if (CropRegistry.bySeed(seed) != null) {
                continue;
            }
            String id = buildId(plantId);
            if (id == null || CropRegistry.isRegistered(id)) {
                continue;
            }
            try {
                CropRegistry.register(new CropSpec(
                        id,
                        seed,
                        seed,
                        block,
                        seed.getDescriptionId(),
                        plantId.getNamespace(),
                        "source.nsukcropapi." + plantId.getNamespace(),
                        FarmlandCropWork.INSTANCE));
                added++;
            } catch (RuntimeException exception) {
                NsukCropApi.LOGGER.debug("[nsukcropapi] 自动发现跳过 {}: {}", plantId, exception.getMessage());
            }
        }
        if (added > 0) {
            NsukCropApi.LOGGER.info("[nsukcropapi] auto-discovery added {} CropBlock crop(s)", added);
        }
        return added;
    }

    /** {@code <命名空间>_<方块路径>}，超长时退回只取路径。 */
    private static String buildId(ResourceLocation plantId) {
        String full = plantId.getNamespace() + "_" + plantId.getPath();
        if (full.length() <= MAX_ID_LENGTH) {
            return full;
        }
        String path = plantId.getPath();
        if (path.length() <= MAX_ID_LENGTH) {
            return path;
        }
        return null;
    }

    private static Item resolveSeed(Block block, Method accessor) {
        if (accessor != null) {
            try {
                Object value = accessor.invoke(block);
                if (value instanceof ItemLike itemLike) {
                    Item item = itemLike.asItem();
                    if (item != Items.AIR) {
                        return item;
                    }
                }
            } catch (Throwable ignored) {
                // 退回方块自身对应的物品
            }
        }
        Item item = block.asItem();
        return item == Items.AIR ? null : item;
    }

    private static Method seedAccessor() {
        try {
            Method method = CropBlock.class.getDeclaredMethod("getBaseSeedId");
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 茎、树叶、树苗、仙人掌、地狱疣、紫颂、葡萄藤架这些都不是"耕地作物"。 */
    private static boolean shouldSkip(Block block) {
        if (block instanceof StemBlock
                || block instanceof AttachedStemBlock
                || block instanceof LeavesBlock
                || block instanceof SaplingBlock) {
            return true;
        }
        String type = block.getClass().getName().toLowerCase(Locale.ROOT);
        if (type.contains("stemblock") || type.contains("leafcrop") || type.contains("sapling")) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id != null && isExcludedPath(id);
    }

    private static boolean isExcludedPath(ResourceLocation id) {
        String path = id.getPath();
        return path.contains("saguaro")
                || path.contains("cactus")
                || path.contains("sapling")
                || path.contains("leaves")
                || path.contains("nether_wart")
                || path.contains("chorus")
                || path.contains("grape")
                || path.contains("trellis")
                || path.contains("rice");
    }

    /** 供界面显示：某个种子当前是否已有注册（避免界面提示与实际不符）。 */
    public static boolean isRegisteredSeed(ItemStack stack) {
        return stack != null && !stack.isEmpty() && CropRegistry.bySeed(stack.getItem()) != null;
    }
}

package com.nsukcropapi.nsuk;

import com.nsukcropapi.NsukCropApi;
import common.cn.nsimu.simukraft.material.WorkContainerService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 箱子门面：给 {@code CropWork} 的实现提供"查材料 / 扣材料 / 存产物"三件事，
 * 免得每个附属都去直接依赖 simukraft 的 {@code WorkContainerService}。
 *
 * <p>宿主服务不可用时全部安全降级：查/扣返回 {@code false}，存产物直接掉在地上，
 * 不会因为版本差异丢东西或崩服。</p>
 */
public final class WorkContainers {

    private static volatile boolean depositWarned;

    private WorkContainers() {
    }

    /** 箱子列表里有没有指定物品。 */
    public static boolean has(ServerLevel level, List<BlockPos> chests, Item item) {
        if (level == null || chests == null || chests.isEmpty() || item == null || item == Items.AIR) {
            return false;
        }
        try {
            return WorkContainerService.hasItem(level, chests, item);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 从箱子列表里扣掉一个指定物品，扣到了返回 true。 */
    public static boolean consume(ServerLevel level, List<BlockPos> chests, Item item) {
        if (level == null || chests == null || chests.isEmpty() || item == null || item == Items.AIR) {
            return false;
        }
        try {
            return WorkContainerService.consumeItem(level, chests, item);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 把一叠物品塞进箱子列表，返回塞不下的余量。
     *
     * <p>返回 {@code ItemStack.EMPTY} 表示全部塞进去了；返回原栈表示一个都没塞进去。
     * 宿主接口不可用时同样原样返回，调用方据此决定要不要掉在地上。</p>
     */
    public static ItemStack insert(ServerLevel level, List<BlockPos> chests, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack == null ? ItemStack.EMPTY : stack;
        }
        if (level == null || chests == null || chests.isEmpty()) {
            return stack;
        }
        try {
            ItemStack remainder = WorkContainerService.insertIntoAny(level, chests, stack);
            return remainder == null ? ItemStack.EMPTY : remainder;
        } catch (Throwable t) {
            return stack;
        }
    }

    /** 把产物存进箱子列表；一个都存不下时掉在 fallback 位置。 */
    public static void deposit(ServerLevel level, List<BlockPos> chests, List<ItemStack> drops, BlockPos fallback) {
        if (level == null || drops == null || drops.isEmpty()) {
            return;
        }
        if (chests != null && !chests.isEmpty()) {
            try {
                WorkContainerService.depositDropsOrDrop(level, chests, drops, fallback);
                return;
            } catch (Throwable t) {
                if (!depositWarned) {
                    depositWarned = true;
                    NsukCropApi.LOGGER.warn("[nsukcropapi] 宿主箱子接口不可用，产物改为掉落在原地", t);
                }
            }
        }
        if (fallback == null) {
            return;
        }
        for (ItemStack stack : drops) {
            if (stack != null && !stack.isEmpty()) {
                Block.popResource(level, fallback, stack);
            }
        }
    }
}

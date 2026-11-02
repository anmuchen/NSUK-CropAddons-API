package com.nsukcropapi.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 一种可交给雇农种植的作物。
 *
 * @param id            农田盒存档里记录的作物 id（同一时刻只允许一个作物）
 * @param seed          种子物品（从箱子消耗/补种）
 * @param icon          界面里显示的图标物品
 * @param plantBlock    作物方块
 * @param nameKey       显示名语言键；为空时退回种子物品名
 * @param sourceModId   提供该作物的模组 id（界面按它分组）
 * @param groupNameKey  分组标题语言键；为空时使用模组显示名
 * @param work          作业策略；为空时使用普通耕地实现
 */
public record CropSpec(
        String id,
        Item seed,
        Item icon,
        Block plantBlock,
        String nameKey,
        String sourceModId,
        String groupNameKey,
        CropWork work
) {

    /** 兼容旧签名：不指定作业策略时走默认的耕地作物实现。 */
    public CropSpec(String id, Item seed, Item icon, Block plantBlock, String nameKey,
                    String sourceModId, String groupNameKey) {
        this(id, seed, icon, plantBlock, nameKey, sourceModId, groupNameKey, null);
    }

    /** 快捷构造：农田盒 id 为 {@code <sourceModId>_<path>}。 */
    public static CropSpec of(String sourceModId, String path, Item seed, Item icon, Block plantBlock, String nameKey) {
        return new CropSpec(sourceModId + "_" + path, seed, icon, plantBlock, nameKey, sourceModId, null);
    }

    public static CropSpec of(String sourceModId, String path, Item seed, Item icon, Block plantBlock,
                              String nameKey, String groupNameKey) {
        return new CropSpec(sourceModId + "_" + path, seed, icon, plantBlock, nameKey, sourceModId, groupNameKey);
    }

    public String displayNameKey() {
        return this.nameKey != null && !this.nameKey.isBlank() ? this.nameKey : this.seed.getDescriptionId();
    }

    /** 永不为空的作业策略。 */
    public CropWork workOrDefault() {
        return this.work != null ? this.work : CropWork.PLAIN;
    }
}

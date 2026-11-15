package com.nsukcropapi.api;

import common.cn.nsimu.simukraft.farmland.FarmCrop;

import java.util.ArrayList;
import java.util.List;

/**
 * 宿主自带的六种作物（小麦 / 胡萝卜 / 马铃薯 / 甜菜 / 西瓜 / 南瓜）的只读查询。
 *
 * <p>附属在显示"这个农田盒种的是什么"时，常常需要先判断 id 是不是宿主内置作物，
 * 是的话直接用宿主的翻译键。有了这层封装，附属不必 import {@code FarmCrop}。</p>
 */
public final class BuiltinCrops {

    private BuiltinCrops() {
    }

    /** 内置作物的翻译键；不是内置作物时返回 {@code null}。 */
    public static String translationKey(String cropId) {
        if (cropId == null || cropId.isBlank()) {
            return null;
        }
        try {
            FarmCrop crop = FarmCrop.fromId(cropId);
            return crop == null ? null : crop.translationKey();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 是否为宿主内置作物。 */
    public static boolean isBuiltin(String cropId) {
        return translationKey(cropId) != null;
    }

    /** 全部内置作物 id（按枚举声明顺序）。 */
    public static List<String> ids() {
        try {
            List<String> out = new ArrayList<>();
            for (FarmCrop crop : FarmCrop.values()) {
                out.add(crop.id());
            }
            return out;
        } catch (Throwable ignored) {
            return List.of();
        }
    }
}

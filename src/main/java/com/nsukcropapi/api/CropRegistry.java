package com.nsukcropapi.api;

import net.minecraft.world.item.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局作物注册表。各作物附属在 {@code FMLCommonSetupEvent} 里注册自己的作物即可，
 * 不需要再写任何 Mixin / 界面 / 存档代码。
 */
public final class CropRegistry {

    private static final Map<String, CropSpec> BY_ID = new LinkedHashMap<>();
    private static volatile List<CropSpec> snapshot = List.of();

    private CropRegistry() {
    }

    public static synchronized void register(CropSpec spec) {
        if (spec == null || spec.id() == null || spec.id().isBlank()) {
            return;
        }
        if (BY_ID.putIfAbsent(spec.id(), spec) == null) {
            snapshot = List.copyOf(BY_ID.values());
        }
    }

    public static synchronized void registerAll(List<CropSpec> specs) {
        if (specs == null) {
            return;
        }
        boolean changed = false;
        for (CropSpec spec : specs) {
            if (spec != null && spec.id() != null && !spec.id().isBlank() && BY_ID.putIfAbsent(spec.id(), spec) == null) {
                changed = true;
            }
        }
        if (changed) {
            snapshot = List.copyOf(BY_ID.values());
        }
    }

    public static List<CropSpec> all() {
        return snapshot;
    }

    public static CropSpec byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static CropSpec bySeed(Item item) {
        if (item == null) {
            return null;
        }
        for (CropSpec spec : snapshot) {
            if (spec.seed() == item) {
                return spec;
            }
        }
        return null;
    }

    public static boolean isRegistered(String id) {
        return id != null && BY_ID.containsKey(id);
    }

    /** 解析玩家输入：完整 id / 短名 / 其它附属的原始 id。 */
    public static String resolveId(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String trimmed = input.trim();
        if (BY_ID.containsKey(trimmed)) {
            return trimmed;
        }
        for (CropSpec spec : snapshot) {
            if (spec.id().endsWith("_" + trimmed)) {
                return spec.id();
            }
        }
        return trimmed;
    }
}

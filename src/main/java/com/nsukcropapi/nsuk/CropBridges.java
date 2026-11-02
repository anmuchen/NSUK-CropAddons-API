package com.nsukcropapi.nsuk;

import com.mojang.logging.LogUtils;
import com.nsukcropapi.api.CropRegistry;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧版作物附属（各自 mixin）的目录桥接，用于在统一界面里并列显示它们的作物。
 */
public final class CropBridges {

    public record Entry(String id, Item seed, String sourceModId) {
    }

    public record Catalog(String modId, List<Entry> entries) {
    }

    private static final String[][] SPECS = {
            {"letsdocropaddons", "com.xiaolianganmuchen.letsdocropaddons.crop.CropCatalog"},
            {"nsukdelightcropaddons", "com.xiaolianganmuchen.nsukdelightcropaddons.crop.CropCatalog"},
            {"kaleidoscopecropaddons", "com.xiaolianganmuchen.kaleidoscopecropaddons.crop.CropCatalog"},
            {"flavorimmersedcropaddons", "com.xiaolianganmuchen.flavorimmersedcropaddons.crop.CropCatalog"}
    };

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean logged;

    private CropBridges() {
    }

    public static List<Catalog> catalogs() {
        List<Catalog> result = new ArrayList<>();
        StringBuilder summary = new StringBuilder();
        for (String[] spec : SPECS) {
            List<Entry> entries = collect(spec[1], spec[0]);
            if (!entries.isEmpty()) {
                result.add(new Catalog(spec[0], entries));
                summary.append(spec[0]).append('=').append(entries.size()).append(' ');
            }
        }
        if (!logged) {
            logged = true;
            LOGGER.info("[nsukcropapi] bridged crop catalogs: {}", summary.length() == 0 ? "none" : summary.toString().trim());
        }
        return result;
    }

    public static boolean isSelectable(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        for (String[] spec : SPECS) {
            try {
                Class<?> catalog = Class.forName(spec[1], false, CropBridges.class.getClassLoader());
                Object result = catalog.getMethod("isSelectable", String.class).invoke(null, id);
                if (result instanceof Boolean selectable && selectable) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 未安装 / 版本不匹配
            }
        }
        return false;
    }

    public static String sourceOf(String id) {
        for (String[] spec : SPECS) {
            try {
                Class<?> catalog = Class.forName(spec[1], false, CropBridges.class.getClassLoader());
                Object result = catalog.getMethod("isSelectable", String.class).invoke(null, id);
                if (result instanceof Boolean selectable && selectable) {
                    return spec[0];
                }
            } catch (Throwable ignored) {
                // ignore
            }
        }
        return "";
    }

    private static List<Entry> collect(String className, String modId) {
        Map<String, Entry> out = new LinkedHashMap<>();
        try {
            Class<?> catalog = Class.forName(className, false, CropBridges.class.getClassLoader());
            Object value = catalog.getMethod("selectable").invoke(null);
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            Method idMethod = null;
            Method seedMethod = null;
            Method missingMethod = null;
            Method sourceMethod = null;
            for (Object element : list) {
                if (element == null) {
                    continue;
                }
                if (idMethod == null) {
                    Class<?> type = element.getClass();
                    idMethod = type.getMethod("id");
                    seedMethod = type.getMethod("seed");
                    try {
                        missingMethod = type.getMethod("isMissing");
                    } catch (Throwable ignored) {
                        missingMethod = null;
                    }
                    try {
                        sourceMethod = type.getMethod("sourceModId");
                    } catch (Throwable ignored) {
                        sourceMethod = null;
                    }
                }
                if (missingMethod != null) {
                    Object missing = missingMethod.invoke(element);
                    if (missing instanceof Boolean flag && flag) {
                        continue;
                    }
                }
                Object idValue = idMethod.invoke(element);
                Object seedValue = seedMethod.invoke(element);
                if (!(idValue instanceof String id) || id.isBlank() || !(seedValue instanceof Item seed)) {
                    continue;
                }
                // 已经迁到 API 注册表的作物由注册表自己列出，桥接只负责"还没迁移的旧附属"
                if (CropRegistry.isRegistered(id)) {
                    continue;
                }
                String source = modId;
                if (sourceMethod != null) {
                    Object sourceValue = sourceMethod.invoke(element);
                    if (sourceValue instanceof String text && !text.isBlank()) {
                        source = text;
                    }
                }
                out.putIfAbsent(id, new Entry(id, seed, source));
            }
        } catch (Throwable ignored) {
            // 该附属未安装或 API 不同：跳过
        }
        return new ArrayList<>(out.values());
    }
}

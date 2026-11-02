package com.nsukcropapi.crop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 作物目录：附属启动时把 JSON 定义注册进来，作业时按 id 解析。
 *
 * <p>与 letsdocropaddons 旧版的区别：不再依赖配置开关与自动发现——
 * 薄壳附属要种什么完全由它自己 jar 里的 JSON 决定，禁用某个来源由整合包作者
 * 直接删 jar / 删 JSON，避免每个附属各带一套配置。</p>
 */
public final class CropCatalog {

    private static final Map<String, CropSpec> SPECS = new ConcurrentHashMap<>();
    private static final Map<String, String> SOURCE_LABELS = new ConcurrentHashMap<>();

    private CropCatalog() {
    }

    public static void registerSourceLabel(String modId, String translationKey) {
        if (modId != null && !modId.isBlank() && translationKey != null && !translationKey.isBlank()) {
            SOURCE_LABELS.put(modId, translationKey);
        }
    }

    public static boolean register(CropSpec spec) {
        if (spec == null) {
            return false;
        }
        SPECS.put(spec.id(), spec);
        return true;
    }

    /** 用一批定义整体替换某个来源的作物（数据包重载用）。 */
    public static void replaceOrigin(String sourceModId, Collection<CropSpec> next) {
        SPECS.entrySet().removeIf(entry -> sourceModId.equals(entry.getValue().sourceModId()));
        if (next != null) {
            for (CropSpec spec : next) {
                register(spec);
            }
        }
    }

    public static void clear() {
        SPECS.clear();
        SOURCE_LABELS.clear();
    }

    public static boolean contains(String id) {
        return id != null && SPECS.containsKey(id);
    }

    public static Optional<CropSpec> spec(String id) {
        return id != null && !id.isBlank() ? Optional.ofNullable(SPECS.get(id)) : Optional.empty();
    }

    public static Optional<ResolvedCrop> resolve(String id) {
        return spec(id).flatMap(ResolvedCrop::resolve);
    }

    /** 全部可解析作物，按来源分组排序（界面用）。 */
    public static List<ResolvedCrop> selectable() {
        List<ResolvedCrop> crops = new ArrayList<>();
        for (CropSpec spec : SPECS.values()) {
            ResolvedCrop.resolve(spec).ifPresent(crops::add);
        }
        crops.sort(Comparator.comparing(ResolvedCrop::sourceModId).thenComparing(ResolvedCrop::id));
        return List.copyOf(crops);
    }

    public static Map<String, List<ResolvedCrop>> selectableBySource() {
        Map<String, List<ResolvedCrop>> grouped = new LinkedHashMap<>();
        for (ResolvedCrop crop : selectable()) {
            grouped.computeIfAbsent(crop.sourceModId(), ignored -> new ArrayList<>()).add(crop);
        }
        return grouped;
    }

    public static String sourceLabelKey(String modId) {
        return modId != null && !modId.isBlank()
                ? SOURCE_LABELS.getOrDefault(modId, "source.nsukcropapi." + modId)
                : "source.nsukcropapi.unknown";
    }
}

package com.nsukcropapi.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * NSUK 作物 API 的统一配置。
 *
 * <p>所有作物附属共用这一份配置：不再需要每个附属各带一套开关，也不需要在整合包里
 * 删 jar / 删 JSON 才能关掉某个来源。界面上改完即时写回 {@code config/nsukcropapi-common.toml}。</p>
 *
 * <p><b>为什么来源开关是一个"禁用列表"而不是几十个布尔键</b>：装了哪些附属、注册了哪些来源，
 * 只有运行时才知道。{@code ModConfigSpec} 的键是静态声明的，无法在运行时追加，
 * 所以这里只存"被关掉的来源 id"，界面按实际注册的来源动态渲染开关。</p>
 */
public final class CropApiConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue AUTO_DISCOVERY = BUILDER
            .comment("扫描已加载模组里的 CropBlock，把没有 JSON 定义的耕地作物也加进农田盒。",
                    "默认关闭：自动发现的作物只能按方块推断种子与产物，可能出现不想要的结果。")
            .define("autoDiscovery", false);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_SOURCES = BUILDER
            .comment("被关闭的来源 modid（由「NSUK 作物设置」界面维护，通常不需要手改）。",
                    "关闭某个来源后，它的作物不再出现在农田盒的作物选择界面；",
                    "已经种着该作物的农田盒仍会照常运行，不会损坏存档。")
            .defineListAllowEmpty("disabledSources", List.of(), () -> "",
                    value -> value instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CropApiConfig() {
    }

    /** 配置是否已经加载；未加载时所有读取都退回默认值。 */
    public static boolean ready() {
        try {
            return SPEC.isLoaded();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean autoDiscovery() {
        try {
            return ready() && AUTO_DISCOVERY.get();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void setAutoDiscovery(boolean value) {
        try {
            AUTO_DISCOVERY.set(value);
        } catch (RuntimeException ignored) {
        }
    }

    /** 当前被关闭的来源（小写）。 */
    public static Set<String> disabledSources() {
        try {
            if (!ready()) {
                return Set.of();
            }
            return DISABLED_SOURCES.get().stream()
                    .map(value -> String.valueOf(value).toLowerCase(Locale.ROOT))
                    .filter(value -> !value.isBlank())
                    .collect(Collectors.toSet());
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }

    public static boolean isSourceEnabled(String modId) {
        if (modId == null || modId.isBlank()) {
            return true;
        }
        return !disabledSources().contains(modId.toLowerCase(Locale.ROOT));
    }

    public static void setSourceEnabled(String modId, boolean enabled) {
        if (modId == null || modId.isBlank()) {
            return;
        }
        String key = modId.toLowerCase(Locale.ROOT);
        List<String> next = new ArrayList<>(disabledSources());
        next.removeIf(value -> value.equalsIgnoreCase(key));
        if (!enabled) {
            next.add(key);
        }
        try {
            DISABLED_SOURCES.set(next);
        } catch (RuntimeException ignored) {
        }
    }

    /** 把当前值写回磁盘。界面关闭时调用。 */
    public static void save() {
        try {
            if (ready()) {
                SPEC.save();
            }
        } catch (RuntimeException ignored) {
        }
    }
}

package com.nsukcropapi;

import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.crop.CropCatalog;
import com.nsukcropapi.crop.CropJsonLoader;
import com.nsukcropapi.crop.ResolvedCrop;
import com.nsukcropapi.crop.ToolkitCropWork;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 附属启动入口：把 jar 里的作物 JSON 注册进 API。
 *
 * <p>薄壳附属的主类在 commonSetup 里调一次即可：</p>
 * <pre>
 * CropAddons.bootstrap(MODID, "nsuk_crops", "farmersdelight.json", "croptopia.json");
 * </pre>
 *
 * <p>作物 id 直接沿用 JSON 里的 {@code prefix_key}，与旧版附属保持一致，
 * 因此旧存档里已经选好作物的农田盒会被 API 自动认领，不需要玩家重选。</p>
 */
public final class CropAddons {

    private CropAddons() {
    }

    /**
     * @param anchor        附属自己的类（用它的类加载器读 jar 内资源，避免跨模组资源不可见）
     * @param modId         附属 mod id（用于定位 jar 内的 {@code data/<modId>/<dir>/}）
     * @param jsonDir       作物 JSON 所在目录名
     * @param builtinFiles  要加载的文件名；<b>留空即自动扫描该目录下全部 json</b>——
     *                      以后新增作物只要往目录里丢文件，不用改这行代码
     */
    public static void bootstrap(Class<?> anchor, String modId, String jsonDir, String... builtinFiles) {
        List<String> files = new ArrayList<>(List.of(builtinFiles));
        if (files.isEmpty()) {
            files.addAll(JarIndex.jsonNames(anchor, modId, jsonDir));
            NsukCropApi.LOGGER.info("[nsukcropapi] {} 自动发现 {} 个作物定义文件", modId, files.size());
        }
        List<CropSpec> registered = new ArrayList<>();
        for (String file : files) {
            String path = "/data/" + modId + "/" + jsonDir + "/" + file;
            int before = registered.size();
            int parsed = 0;
            try (InputStream in = anchor.getResourceAsStream(path)) {
                if (in == null) {
                    NsukCropApi.LOGGER.warn("[nsukcropapi] 找不到作物定义 {}（{}）", file, modId);
                    continue;
                }
                List<com.nsukcropapi.crop.CropSpec> specs = CropJsonLoader.parse(in, modId);
                parsed = specs.size();
                for (com.nsukcropapi.crop.CropSpec spec : specs) {
                    register(spec).ifPresent(registered::add);
                }
            } catch (Exception exception) {
                NsukCropApi.LOGGER.warn("[nsukcropapi] 读取 {} 失败", file, exception);
            }
            int added = registered.size() - before;
            if (added > 0) {
                NsukCropApi.LOGGER.info("[nsukcropapi] {}: {} 种作物可用", file, added);
            } else if (parsed > 0) {
                NsukCropApi.LOGGER.info("[nsukcropapi] {}: 解析 {} 种，均不可用（来源模组未安装？）", file, parsed);
            }
        }
        CropRegistry.registerAll(registered);
        NsukCropApi.LOGGER.info("[nsukcropapi] {} 注册 {} 种作物", modId, registered.size());
    }

    /** 把作业库的 CropSpec 翻译成 API 的 CropSpec（id/图标/名称/分组全部沿用原定义）。 */
    private static java.util.Optional<CropSpec> register(com.nsukcropapi.crop.CropSpec spec) {
        ResolvedCrop crop = ResolvedCrop.resolve(spec).orElse(null);
        if (crop == null || crop.id() == null || crop.id().isBlank()) {
            return java.util.Optional.empty();
        }
        Item seed = crop.seed();
        Block plant = crop.plantBlock();
        if (seed == null || seed == Items.AIR || plant == null || plant == Blocks.AIR) {
            NsukCropApi.LOGGER.warn("[nsukcropapi] 作物 {} 的种子或作物方块缺失，跳过", crop.id());
            return java.util.Optional.empty();
        }
        CropCatalog.register(spec);
        return java.util.Optional.of(new CropSpec(
                crop.id(),
                seed,
                seed,
                plant,
                crop.translationKey(),
                crop.sourceModId(),
                CropCatalog.sourceLabelKey(crop.sourceModId()),
                ToolkitCropWork.INSTANCE));
    }
}

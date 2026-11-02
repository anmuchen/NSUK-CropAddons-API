package com.nsukcropapi.crop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 读取附属 jar 里的作物 JSON（{@code data/<modid>/<dir>/*.json}）。
 *
 * <p>字段与乐事附属 1.x 完全一致，三个附属的 JSON 可以直接搬过来用：
 * <pre>
 * {
 *   "mod": "vinery", "prefix": "vin", "label": "source.xxx.vinery", "discover": false,
 *   "crops": [
 *     {"id": "red", "seed": "vinery:red_grape_seeds", "plant": "vinery:grapevine_stem",
 *      "layout": "vine_stack", "support_block": "vinery:grapevine_stem",
 *      "fruit_item": "vinery:red_grape", "plant_properties": {"grape": "red"},
 *      "soil_blocks": ["minecraft:dirt"], "soil_item": "minecraft:dirt"}
 *   ]
 * }
 * </pre>
 */
public final class CropJsonLoader {

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private CropJsonLoader() {
    }

    /** 解析一个 JSON 输入流；文件级字段（mod/prefix/label）会应用到其中每个作物。 */
    public static List<CropSpec> parse(InputStream stream, String fallbackModId) {
        List<CropSpec> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                return out;
            }
            JsonObject json = root.getAsJsonObject();
            String modId = string(json, "mod", fallbackModId);
            String prefix = string(json, "prefix", modId);
            String label = json.has("label") ? json.get("label").getAsString() : "source.nsukcropapi." + modId;
            CropCatalog.registerSourceLabel(modId, label);
            for (JsonElement element : array(json, "crops")) {
                if (element != null && element.isJsonObject()) {
                    parseCrop(modId, prefix, element.getAsJsonObject()).ifPresent(out::add);
                }
            }
        } catch (Exception ignored) {
            // 单个文件解析失败不影响其它文件
        }
        return out;
    }

    private static Optional<CropSpec> parseCrop(String modId, String prefix, JsonObject json) {
        String key = string(json, "id", "");
        ResourceLocation seedId = CropIds.parse(string(json, "seed", ""), modId);
        if (key.isBlank() || seedId == null) {
            return Optional.empty();
        }
        String id = CropIds.compose(prefix, key);
        ResourceLocation plantId = CropIds.parse(string(json, "plant", ""), modId);
        List<ResourceLocation> extra = idList(json, "harvest_blocks", modId);
        List<ResourceLocation> growth = idList(json, "growth_blocks", modId);
        ResourceLocation produceId = CropIds.parse(string(json, "produce", ""), modId);
        CropLayout layout = CropLayout.fromName(string(json, "layout", "full"));
        boolean waterlog = json.has("waterlog") && json.get("waterlog").getAsBoolean();
        boolean allowNonCrop = json.has("allow_non_crop_block") && json.get("allow_non_crop_block").getAsBoolean();
        int harvestHeight = json.has("harvest_height") ? json.get("harvest_height").getAsInt() : 0;
        boolean harvestMaxAge = !json.has("harvest_max_age") || json.get("harvest_max_age").getAsBoolean();
        ResourceLocation fruitId = CropIds.parse(string(json, "fruit_block", ""), modId);
        ResourceLocation supportId = CropIds.parse(string(json, "support_block", ""), modId);
        ResourceLocation plantTagId = CropIds.parse(string(json, "plant_tag", ""), modId);
        ResourceLocation soilTagId = CropIds.parse(string(json, "soil_tag", ""), modId);
        ResourceLocation soilItemId = CropIds.parse(string(json, "soil_item", ""), modId);
        ResourceLocation fruitItemId = CropIds.parse(string(json, "fruit_item", ""), modId);
        List<ResourceLocation> soilExcludes = idList(json, "soil_exclude", modId);
        List<ResourceLocation> soilBlocks = idList(json, "soil_blocks", modId);
        boolean plantMature = json.has("plant_mature") && json.get("plant_mature").getAsBoolean();
        Map<String, String> plantProperties = stringMap(json, "plant_properties");
        boolean fillFarmland = json.has("fill_farmland") && json.get("fill_farmland").getAsBoolean();
        boolean replaceSoil = json.has("replace_soil") && json.get("replace_soil").getAsBoolean();
        boolean noSoil = json.has("no_soil") && json.get("no_soil").getAsBoolean();
        boolean allowWaterTrough = json.has("allow_water_trough") && json.get("allow_water_trough").getAsBoolean();
        boolean hidden = json.has("hidden") && json.get("hidden").getAsBoolean();
        boolean noWater = json.has("no_water") && json.get("no_water").getAsBoolean();

        try {
            return Optional.of(CropSpec.builder(id)
                    .sourceModId(modId)
                    .sourcePrefix(prefix)
                    .seedId(seedId)
                    .plantBlockId(plantId)
                    .extraHarvestBlockIds(extra)
                    .growthBlockIds(growth)
                    .layout(layout)
                    .produceBlockId(produceId)
                    .waterlogOnPlant(waterlog)
                    .allowNonCropBlock(allowNonCrop || layout == CropLayout.TRELLIS
                            || layout == CropLayout.LATTICE || layout == CropLayout.VINE_STACK
                            || layout == CropLayout.CANOPY)
                    .harvestHeight(harvestHeight)
                    .harvestMaxAge(harvestMaxAge)
                    .fruitBlockId(fruitId)
                    .supportBlockId(supportId)
                    .plantTagId(plantTagId)
                    .soilTagId(soilTagId)
                    .soilItemId(soilItemId)
                    .fruitItemId(fruitItemId)
                    .soilExcludeIds(soilExcludes)
                    .soilBlockIds(soilBlocks)
                    .plantMature(plantMature)
                    .plantProperties(plantProperties)
                    .fillFarmland(fillFarmland)
                    .replaceSoil(replaceSoil)
                    .noSoil(noSoil)
                    .allowWaterTrough(allowWaterTrough)
                    .hidden(hidden)
                    .noWater(noWater)
                    .build());
        } catch (IllegalArgumentException exception) {
            com.nsukcropapi.NsukCropApi.LOGGER.warn("[nsukcropapi] 跳过作物 {}: {}", id, exception.getMessage());
            return Optional.empty();
        }
    }

    private static List<JsonElement> array(JsonObject json, String key) {
        List<JsonElement> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                out.add(element);
            }
        }
        return out;
    }

    private static List<ResourceLocation> idList(JsonObject json, String key, String modId) {
        List<ResourceLocation> out = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            JsonArray array = json.getAsJsonArray(key);
            for (JsonElement element : array) {
                if (element != null && element.isJsonPrimitive()) {
                    ResourceLocation id = CropIds.parse(element.getAsString(), modId);
                    if (id != null) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
    }

    private static Map<String, String> stringMap(JsonObject json, String key) {
        Map<String, String> out = new LinkedHashMap<>();
        if (json.has(key) && json.get(key).isJsonObject()) {
            JsonObject object = json.getAsJsonObject(key);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    out.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        return out;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }
}

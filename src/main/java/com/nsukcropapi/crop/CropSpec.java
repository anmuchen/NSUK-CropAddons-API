package com.nsukcropapi.crop;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record CropSpec(
   String id,
   String sourceModId,
   String sourcePrefix,
   ResourceLocation seedId,
   ResourceLocation plantBlockId,
   List<ResourceLocation> extraHarvestBlockIds,
   List<ResourceLocation> growthBlockIds,
   CropLayout layout,
   ResourceLocation produceBlockId,
   boolean waterlogOnPlant,
   boolean allowNonCropBlock,
   int harvestHeight,
   boolean harvestMaxAge,
   CropOrigin origin,
   ResourceLocation fruitBlockId,
   ResourceLocation supportBlockId,
   ResourceLocation soilTagId,
   ResourceLocation soilItemId,
   ResourceLocation fruitItemId,
   List<ResourceLocation> soilExcludeIds,
   List<ResourceLocation> soilBlockIds,
   boolean plantMature,
   Map<String, String> plantProperties,
   ResourceLocation plantTagId,
   /** 普通作物：下方不是耕地时，用箱子里的泥土铺成耕地（可替换石头等原方块）。 */
   boolean fillFarmland,
   /** 自定义土壤：下方方块不匹配时，无条件换成该作物的土壤（可替换石头等原方块）。 */
   boolean replaceSoil,
   /** 不需要任何土壤：下方是完整方块就能种（马勃菌）。 */
   boolean noSoil,
   /** 允许在水渠占位行上作业（配合 noSoil / 自定义作物使用）。 */
   boolean allowWaterTrough,
   /** 不在作物选择界面显示（存档里已有的农田盒仍可继续使用）。 */
   boolean hidden,
   /** 不挖水渠（只给明确不需要水渠的作物，例如烘焙坊生咖啡豆）。 */
   boolean noWater
) {
   public static final int MAX_ID_LENGTH = 32;
   public static final int MAX_HARVEST_HEIGHT = 8;

   public CropSpec(
      String id,
      String sourceModId,
      String sourcePrefix,
      ResourceLocation seedId,
      ResourceLocation plantBlockId,
      List<ResourceLocation> extraHarvestBlockIds,
      List<ResourceLocation> growthBlockIds,
      CropLayout layout,
      ResourceLocation produceBlockId,
      boolean waterlogOnPlant,
      boolean allowNonCropBlock,
      int harvestHeight,
      boolean harvestMaxAge,
      CropOrigin origin,
      ResourceLocation fruitBlockId,
      ResourceLocation supportBlockId,
      ResourceLocation soilTagId,
      ResourceLocation soilItemId,
      ResourceLocation fruitItemId,
   List<ResourceLocation> soilExcludeIds,
   List<ResourceLocation> soilBlockIds,
   boolean plantMature,
   Map<String, String> plantProperties,
   ResourceLocation plantTagId,
   boolean fillFarmland,
   boolean replaceSoil,
   boolean noSoil,
   boolean allowWaterTrough,
   boolean hidden,
   boolean noWater
) {
   Objects.requireNonNull(id, "id");
      Objects.requireNonNull(sourceModId, "sourceModId");
      Objects.requireNonNull(sourcePrefix, "sourcePrefix");
      Objects.requireNonNull(seedId, "seedId");
      extraHarvestBlockIds = extraHarvestBlockIds == null ? List.of() : List.copyOf(extraHarvestBlockIds);
      growthBlockIds = growthBlockIds == null ? List.of() : List.copyOf(growthBlockIds);
      soilExcludeIds = soilExcludeIds == null ? List.of() : List.copyOf(soilExcludeIds);
      soilBlockIds = soilBlockIds == null ? List.of() : List.copyOf(soilBlockIds);
      plantProperties = plantProperties == null ? Map.of() : Map.copyOf(plantProperties);
      layout = layout == null ? CropLayout.FULL : layout;
      origin = origin == null ? CropOrigin.API : origin;
      harvestHeight = Math.max(0, Math.min(8, harvestHeight));
      if (id.length() > 32) {
         throw new IllegalArgumentException("Crop id '" + id + "' exceeds NSUK farmland packet limit of 32");
      } else {
         this.id = id;
         this.sourceModId = sourceModId;
         this.sourcePrefix = sourcePrefix;
         this.seedId = seedId;
         this.plantBlockId = plantBlockId;
         this.extraHarvestBlockIds = extraHarvestBlockIds;
         this.growthBlockIds = growthBlockIds;
         this.layout = layout;
         this.produceBlockId = produceBlockId;
         this.waterlogOnPlant = waterlogOnPlant;
         this.allowNonCropBlock = allowNonCropBlock;
         this.harvestHeight = harvestHeight;
         this.harvestMaxAge = harvestMaxAge;
         this.origin = origin;
         this.fruitBlockId = fruitBlockId;
         this.supportBlockId = supportBlockId;
         this.soilTagId = soilTagId;
         this.soilItemId = soilItemId;
         this.fruitItemId = fruitItemId;
         this.soilExcludeIds = soilExcludeIds;
         this.soilBlockIds = soilBlockIds;
         this.plantMature = plantMature;
         this.plantProperties = plantProperties;
         this.plantTagId = plantTagId;
         this.fillFarmland = fillFarmland;
         this.replaceSoil = replaceSoil;
         this.noSoil = noSoil;
         this.allowWaterTrough = allowWaterTrough;
         this.hidden = hidden;
         this.noWater = noWater;
      }
   }

   public static CropSpec.Builder builder(String id) {
      return new CropSpec.Builder(id);
   }

   public static final class Builder {
      private final String id;
      private String sourceModId = "";
      private String sourcePrefix = "";
      private ResourceLocation seedId;
      private ResourceLocation plantBlockId;
      private List<ResourceLocation> extraHarvestBlockIds = List.of();
      private List<ResourceLocation> growthBlockIds = List.of();
      private CropLayout layout = CropLayout.FULL;
      private ResourceLocation produceBlockId;
      private boolean waterlogOnPlant;
      private boolean allowNonCropBlock;
      private int harvestHeight;
      private boolean harvestMaxAge = true;
      private CropOrigin origin = CropOrigin.API;
      private ResourceLocation fruitBlockId;
      private ResourceLocation supportBlockId;
      private ResourceLocation soilTagId;
      private ResourceLocation soilItemId;
      private ResourceLocation fruitItemId;
      private List<ResourceLocation> soilExcludeIds = List.of();
      private List<ResourceLocation> soilBlockIds = List.of();
      private boolean plantMature;
      private Map<String, String> plantProperties = Map.of();
      private ResourceLocation plantTagId;
      private boolean fillFarmland;
      private boolean replaceSoil;
      private boolean noSoil;
      private boolean allowWaterTrough;
      private boolean hidden;
      private boolean noWater;

      private Builder(String id) {
         this.id = id;
      }

      public CropSpec.Builder sourceModId(String sourceModId) {
         this.sourceModId = sourceModId;
         return this;
      }

      public CropSpec.Builder sourcePrefix(String sourcePrefix) {
         this.sourcePrefix = sourcePrefix;
         return this;
      }

      public CropSpec.Builder seedId(ResourceLocation seedId) {
         this.seedId = seedId;
         return this;
      }

      public CropSpec.Builder plantBlockId(ResourceLocation plantBlockId) {
         this.plantBlockId = plantBlockId;
         return this;
      }

      public CropSpec.Builder extraHarvestBlockIds(List<ResourceLocation> extraHarvestBlockIds) {
         this.extraHarvestBlockIds = extraHarvestBlockIds;
         return this;
      }

      public CropSpec.Builder growthBlockIds(List<ResourceLocation> growthBlockIds) {
         this.growthBlockIds = growthBlockIds;
         return this;
      }

      public CropSpec.Builder layout(CropLayout layout) {
         this.layout = layout;
         return this;
      }

      public CropSpec.Builder produceBlockId(ResourceLocation produceBlockId) {
         this.produceBlockId = produceBlockId;
         return this;
      }

      public CropSpec.Builder waterlogOnPlant(boolean waterlogOnPlant) {
         this.waterlogOnPlant = waterlogOnPlant;
         return this;
      }

      public CropSpec.Builder allowNonCropBlock(boolean allowNonCropBlock) {
         this.allowNonCropBlock = allowNonCropBlock;
         return this;
      }

      public CropSpec.Builder harvestHeight(int harvestHeight) {
         this.harvestHeight = harvestHeight;
         return this;
      }

      public CropSpec.Builder harvestMaxAge(boolean harvestMaxAge) {
         this.harvestMaxAge = harvestMaxAge;
         return this;
      }

      public CropSpec.Builder origin(CropOrigin origin) {
         this.origin = origin;
         return this;
      }

      public CropSpec.Builder fruitBlockId(ResourceLocation fruitBlockId) {
         this.fruitBlockId = fruitBlockId;
         return this;
      }

      public CropSpec.Builder supportBlockId(ResourceLocation supportBlockId) {
         this.supportBlockId = supportBlockId;
         return this;
      }

      public CropSpec.Builder soilTagId(ResourceLocation soilTagId) {
         this.soilTagId = soilTagId;
         return this;
      }

      public CropSpec.Builder soilItemId(ResourceLocation soilItemId) {
         this.soilItemId = soilItemId;
         return this;
      }

      public CropSpec.Builder fruitItemId(ResourceLocation fruitItemId) {
         this.fruitItemId = fruitItemId;
         return this;
      }

      /** 土壤标签里虽然写了、但不允许农民使用（也不认作有效土壤）的方块。 */
      public CropSpec.Builder soilExcludeIds(List<ResourceLocation> soilExcludeIds) {
         this.soilExcludeIds = soilExcludeIds;
         return this;
      }

      /**
       * 宿主模组没给土壤标签时（例如回响作物的幽匿催发体是硬编码判断的），
       * 直接在这里列出可用的土壤方块。与 {@code soil_tag} 同时给出时取并集。
       */
      public CropSpec.Builder soilBlockIds(List<ResourceLocation> soilBlockIds) {
         this.soilBlockIds = soilBlockIds;
         return this;
      }

      /**
       * 直接把作物种成「成熟」状态。
       *
       * <p>两类作物会用到：<br>
       * 1) 次元酒窖的绯红/诡异葡萄 —— 随机生长要求正下方是菌岩，而农民布局里正下方要留给果实；<br>
       * 2) 葡园酒香（Vinery）的葡萄藤杆 —— 它的自然生长与播种都要求「正下方也是藤杆」，
       * 农民是一格一株的布局，堆不出柱子，只能种下去就满熟。</p>
       */
      public CropSpec.Builder plantMature(boolean plantMature) {
         this.plantMature = plantMature;
         return this;
      }

      /**
       * 种下时一并写入的方块状态属性。
       *
       * <p>葡园酒香的葡萄种类是写在方块状态上的（{@code grape=red|white|taiga_red|...}），
       * 单靠放置方块本体无法决定收哪一种葡萄，必须在这里显式指定。
       * 属性不存在时静默跳过；值按 {@code Property#getName} 反查，写错值不会抛异常。</p>
       */
      public CropSpec.Builder plantProperties(Map<String, String> plantProperties) {
         this.plantProperties = plantProperties;
         return this;
      }

      /**
       * 「作物本体」的方块标签：格架类作物（见 {@link CropLayout#LATTICE}）的本体是
       * 一整族方块（{@code vinery:lattice} 覆盖 10 种木质格架），单靠 {@code plant}
       * 写一个代表方块认不全，玩家往田里放丛林木格架也该被识别。
       * 标签里每个方块都算本体，箱子里的任一变体都可作为消耗材料。
       */
      public CropSpec.Builder plantTagId(ResourceLocation plantTagId) {
         this.plantTagId = plantTagId;
         return this;
      }

      /** 普通作物：用箱子里的泥土把下方铺成耕地（可替换石头等非保护方块）。 */
      public CropSpec.Builder fillFarmland(boolean fillFarmland) {
         this.fillFarmland = fillFarmland;
         return this;
      }

      /** 自定义土壤：下方方块不匹配时无条件换成该作物的土壤。 */
      public CropSpec.Builder replaceSoil(boolean replaceSoil) {
         this.replaceSoil = replaceSoil;
         return this;
      }

      /** 不需要土壤：下方是完整方块就能种。 */
      public CropSpec.Builder noSoil(boolean noSoil) {
         this.noSoil = noSoil;
         return this;
      }

      /** 允许在水渠占位行上继续作业。 */
      public CropSpec.Builder allowWaterTrough(boolean allowWaterTrough) {
         this.allowWaterTrough = allowWaterTrough;
         return this;
      }

      /** 不在作物选择界面显示。 */
      public CropSpec.Builder hidden(boolean hidden) {
         this.hidden = hidden;
         return this;
      }

      /** 不挖水渠（只给明确不需要水渠的作物）。 */
      public CropSpec.Builder noWater(boolean noWater) {
         this.noWater = noWater;
         return this;
      }

      public CropSpec build() {
         return new CropSpec(
            this.id,
            this.sourceModId,
            this.sourcePrefix,
            this.seedId,
            this.plantBlockId,
            this.extraHarvestBlockIds,
            this.growthBlockIds,
            this.layout,
            this.produceBlockId,
            this.waterlogOnPlant,
            this.allowNonCropBlock,
            this.harvestHeight,
            this.harvestMaxAge,
            this.origin,
            this.fruitBlockId,
            this.supportBlockId,
            this.soilTagId,
            this.soilItemId,
            this.fruitItemId,
            this.soilExcludeIds,
            this.soilBlockIds,
            this.plantMature,
            this.plantProperties,
            this.plantTagId,
            this.fillFarmland,
            this.replaceSoil,
            this.noSoil,
            this.allowWaterTrough,
            this.hidden,
            this.noWater
         );
      }
   }
}

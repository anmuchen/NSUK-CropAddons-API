# NSUK：作物 API

把任意作物模组接入 **New: Sim-U-Kraft（simukraft）** 的农田盒系统。

- 模组 ID：`nsukcropapi`
- 版本：1.0.0
- 运行环境：Minecraft 1.21.1 / NeoForge 21.1.228 – 21.1.x
- 前置：`New: Sim-U-Kraft`（simukraft）2.0.0+
- 许可证：GNU General Public License v3.0

---

## 它解决什么问题

simukraft 的农田盒本身只认识原版作物。每接入一个新的作物模组，都要各自处理"怎么铺土、怎么播种、什么时候施骨粉、怎么收割、怎么补种"，还要处理存档兼容与多附属互抢作物 id 的问题。

本模组把这部分抽成公共层，附属只需提供**作物定义数据**：

| 由 API 统一提供 | 附属只需提供 |
| --- | --- |
| 作物注册表与 id 归属仲裁 | 一份描述作物字段的 JSON |
| 五阶段作业流程（铺土 → 播种 → 骨粉 → 收割 → 补种） | 主类中一行 `bootstrap` 调用 |
| 农田盒存档字段的读写 | 可选的自定义 `CropWork` |
| 统一的作物选择界面（多模组分组、可搜索） | — |

---

## 附属接入

### 1. 主类调用一次 bootstrap

```java
package com.example.exampleaddons;

import com.nsukcropapi.CropAddons;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(ExampleAddons.MODID)
public class ExampleAddons {

    public static final String MODID = "exampleaddons";

    public ExampleAddons(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // anchor：用附属自己的类，API 借助它的类加载器读取 jar 内资源
        // "crops"：JSON 所在目录名，对应 data/exampleaddons/crops/
        // 文件名参数留空 → 自动扫描该目录下全部 json
        event.enqueueWork(() -> CropAddons.bootstrap(ExampleAddons.class, MODID, "crops"));
    }
}
```

`bootstrap` 的完整签名为：

```java
public static void bootstrap(Class<?> anchor, String modId, String jsonDir, String... builtinFiles)
```

`builtinFiles` 留空时会调用 `JarIndex.jsonNames(...)` 扫描 `data/<modId>/<jsonDir>/` 下的全部 JSON。因此后续加作物只需往资源目录里丢文件，不必修改 Java 代码。

### 2. 提供作物 JSON

放在 `src/main/resources/data/<modId>/<jsonDir>/` 下。字段定义以源码为准：

```
src/main/java/com/nsukcropapi/crop/CropJsonLoader.java
```

已被现有附属使用的字段包括：

```
seed / plant / layout(full|stem|trellis) / fruit_block / fruit_item / support_block /
soil_tag / soil_blocks / soil_item / soil_exclude / harvest_blocks / harvest_height /
harvest_max_age / plant_mature / plant_properties / waterlog / allow_non_crop_block
```

### 3. 需要特殊作业逻辑时

大多数作物用默认的 `FarmlandCropWork` 即可。若作物需要完全不同的作业方式（例如搭架、特殊土壤判定），实现 `com.nsukcropapi.api.CropWork` 并在 `CropSpec` 中指定。

---

## 以本 API 为前置的附属

| 模组 | 说明 |
| --- | --- |
| 沉浸系列：作物附属 | Let's Do 系列（Farm & Charm / Brewery / Vinery） |
| 森罗物语：作物附属 | Kaleidoscope 系列 |
| 烟火凡人心：作物附属 | Flavor Immersed Daily |
| 潘马斯农场：作物附属 | Pam's HarvestCraft 2 - Crops |
| 乐事系列：作物附属 | Farmer's Delight 系列 |
| NSUK：其它作物附属 | Concoction / Tea Story / Bakeries |

---

## 构建

本工程使用 `build.ps1`（javac + 官方重映射工具），不是 Gradle 工程。

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

产物：`build/libs/nsukcropapi-1.0.0.jar`

### 构建的前置条件（重要）

`build.ps1` 通过脚本位置向上查找 Minecraft 实例目录，并从中读取依赖。因此**源码必须放在实例目录内**：

```
<Minecraft 实例目录>/
├─ <实例名>.json                       # 版本 json，脚本从中读取 NeoForge 版本
├─ <实例名>.jar                       # 原版 client jar
├─ libraries/                         # NeoForge client / universal jar
├─ mods/
│  └─ ldlib2-*.jar                    # 提供 @WrapMethod 等注解
└─ nsukcropapi-dev/                   # 本仓库
   └─ build.ps1
```

脚本接受 `-McRoot` 参数覆盖自动探测的根目录，`-SkipRemap` 参数跳过重映射步骤。

---

## 许可证

GNU General Public License v3.0，全文见 [LICENSE](LICENSE)。

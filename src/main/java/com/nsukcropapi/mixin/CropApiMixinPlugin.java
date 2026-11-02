package com.nsukcropapi.mixin;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 兼容性守门人：用 ASM 读字节码校验 simukraft 的目标方法（绝不能 Class.forName，否则目标类会被提前加载）。
 */
public final class CropApiMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FARMLAND = "common/cn/kafei/simukraft/farmland/";
    private static final String LEVEL = "Lnet/minecraft/server/level/ServerLevel;";
    private static final String BOX = "L" + FARMLAND + "FarmlandBoxData;";
    private static final String LIST = "Ljava/util/List;";
    private static final String POS = "Lnet/minecraft/core/BlockPos;";
    private static final String RESULT = "L" + FARMLAND + "FarmlandWorkResult;";
    private static final String CROP = "L" + FARMLAND + "FarmCrop;";

    private static boolean checked;
    private static boolean serverOk;
    private static boolean clientOk;
    private static boolean openerOk;
    private static boolean stagesOk;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        ensureChecked();
        if (mixinClassName.contains("FarmlandBoxScreenOpenerMixin")) {
            return openerOk;
        }
        if (mixinClassName.contains("FarmlandStageMixin")) {
            return stagesOk;
        }
        return mixinClassName.contains(".v2.client.") ? clientOk : serverOk;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static synchronized void ensureChecked() {
        if (checked) {
            return;
        }
        checked = true;
        ClassLoader loader = CropApiMixinPlugin.class.getClassLoader();
        serverOk = checkServer(loader);
        clientOk = serverOk && checkClient(loader);
        openerOk = serverOk && checkOpener(loader);
        stagesOk = serverOk && checkStages(loader);
        LOGGER.info("[nsukcropapi] simukraft {} integration: server={}, client={}, opener={}, stages={}",
                modVersion(), serverOk, clientOk, openerOk, stagesOk);
        if (!serverOk) {
            LOGGER.warn("[nsukcropapi] simukraft 农田盒 API 不匹配，已跳过联动（作物本体不受影响）");
        }
    }

    private static String modVersion() {
        try {
            return ModList.get().getModContainerById("simukraft")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("?");
        } catch (Throwable t) {
            return "?";
        }
    }

    private static boolean knownVersion() {
        String version = modVersion();
        return version.startsWith("2.2.") || version.startsWith("2.3.");
    }

    private static boolean checkServer(ClassLoader loader) {
        try {
            Set<String> crop = methods(loader, FARMLAND + "FarmCrop");
            if (crop == null) {
                return knownVersion();
            }
            return crop.contains("seed()Lnet/minecraft/world/item/Item;")
                    && crop.contains("layout()L" + FARMLAND + "FarmCrop$Layout;")
                    && crop.contains("isStem()Z")
                    && crop.contains("id()Ljava/lang/String;")
                    && crop.contains("translationKey()Ljava/lang/String;")
                    && crop.contains("shouldPlantAt(II)Z")
                    && crop.contains("plantState()Lnet/minecraft/world/level/block/state/BlockState;")
                    && crop.contains("plantBlock()Lnet/minecraft/world/level/block/Block;")
                    && crop.contains("produceBlock()Lnet/minecraft/world/level/block/Block;")
                    && crop.contains("isOwnPlant(Lnet/minecraft/world/level/block/state/BlockState;)Z")
                    && crop.contains("isMatureFull(Lnet/minecraft/world/level/block/state/BlockState;)Z")
                    && crop.contains("isProduce(Lnet/minecraft/world/level/block/state/BlockState;)Z")
                    && checkData(loader)
                    && checkService(loader)
                    && checkManager(loader)
                    && checkFarming(loader);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean checkData(ClassLoader loader) {
        Set<String> data = methods(loader, FARMLAND + "FarmlandBoxData");
        return data != null
                && data.contains("toTag()Lnet/minecraft/nbt/CompoundTag;")
                && data.contains("fromTag(Lnet/minecraft/nbt/CompoundTag;)L" + FARMLAND + "FarmlandBoxData;")
                && data.contains("setCrop(L" + FARMLAND + "FarmCrop;)V")
                && data.contains("setRunning(Z)V")
                && data.contains("running()Z");
    }

    private static boolean checkService(ClassLoader loader) {
        Set<String> service = methods(loader, FARMLAND + "FarmlandBoxService");
        return service != null
                && service.contains("setCrop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/String;)Z")
                && service.contains("buildView(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)L" + FARMLAND + "FarmlandBoxView;");
    }

    private static boolean checkManager(ClassLoader loader) {
        Set<String> manager = methods(loader, FARMLAND + "FarmlandBoxManager");
        return manager != null
                && manager.contains("get(Lnet/minecraft/server/level/ServerLevel;)L" + FARMLAND + "FarmlandBoxManager;")
                && manager.contains("get(Lnet/minecraft/core/BlockPos;)L" + FARMLAND + "FarmlandBoxData;")
                && manager.contains("getOrCreate(Lnet/minecraft/core/BlockPos;)L" + FARMLAND + "FarmlandBoxData;")
                && manager.contains("persist(L" + FARMLAND + "FarmlandBoxData;)V");
    }

    private static boolean checkFarming(ClassLoader loader) {
        Set<String> farming = methods(loader, FARMLAND + "FarmlandFarmingService");
        return farming != null && farming.contains(
                "tickBox(Lnet/minecraft/server/level/ServerLevel;L" + FARMLAND + "FarmlandBoxManager;L" + FARMLAND
                        + "FarmlandBoxData;L" + FARMLAND + "FarmlandFarmingService$BoxRuntime;J)V");
    }

    /** 五阶段接管的目标方法：缺任何一个就整段不挂，只保留作物数据/界面联动。 */
    private static boolean checkStages(ClassLoader loader) {
        Set<String> farming = methods(loader, FARMLAND + "FarmlandFarmingService");
        if (farming == null || methods(loader, FARMLAND + "FarmlandWorkResult") == null) {
            return false;
        }
        return farming.contains("needsWaterWork(" + LEVEL + BOX + LIST + POS + ")Z")
                && farming.contains("applyWaterWork(" + LEVEL + BOX + LIST + POS + ")" + RESULT)
                && farming.contains("needsTillWork(" + LEVEL + BOX + POS + ")Z")
                && farming.contains("applyTillWork(" + LEVEL + BOX + POS + ")" + RESULT)
                && farming.contains("needsPlantWork(" + LEVEL + BOX + POS + ")Z")
                && farming.contains("applyPlantWork(" + LEVEL + BOX + LIST + POS + ")" + RESULT)
                && farming.contains("needsBonemealWork(" + LEVEL + BOX + LIST + POS + ")Z")
                && farming.contains("applyBonemealWork(" + LEVEL + BOX + LIST + POS + ")" + RESULT)
                && farming.contains("needsHarvestWork(" + LEVEL + BOX + POS + ")Z")
                && farming.contains("applyHarvestWork(" + LEVEL + BOX + LIST + POS + ")" + RESULT)
                && farming.contains("hasSeed(" + LEVEL + LIST + CROP + ")Z")
                && farming.contains("farmerStatusLabel(" + LEVEL + "Ljava/lang/String;" + CROP + ")Ljava/lang/String;")
                && farming.contains("usesGroupedFarmScan(L" + FARMLAND + "FarmlandWorkPhase;)Z");
    }

    private static boolean checkClient(ClassLoader loader) {
        Set<String> screen = methods(loader, "client/cn/kafei/simukraft/client/farmland/FarmlandCropScreen");
        if (screen == null) {
            return knownVersion();
        }
        return screen.contains("open(Lcommon/cn/kafei/simukraft/network/farmland/FarmlandBoxOpenResponsePacket;)V");
    }

    private static boolean checkOpener(ClassLoader loader) {
        Set<String> opener = methods(loader, "client/cn/kafei/simukraft/client/farmland/FarmlandBoxScreenOpener");
        if (opener == null) {
            return knownVersion();
        }
        return opener.contains(
                "cropLine(Lcommon/cn/kafei/simukraft/network/farmland/FarmlandBoxOpenResponsePacket;)Lnet/minecraft/network/chat/Component;");
    }

    private static Set<String> methods(ClassLoader loader, String internalName) {
        String resource = internalName + ".class";
        InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context != null) {
                in = context.getResourceAsStream(resource);
            }
        }
        if (in == null) {
            return null;
        }
        try (InputStream stream = in) {
            Set<String> result = new HashSet<>();
            ClassReader reader = new ClassReader(stream);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    result.add(name + descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        } catch (Throwable t) {
            return null;
        }
    }
}

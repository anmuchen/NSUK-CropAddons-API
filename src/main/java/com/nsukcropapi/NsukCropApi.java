package com.nsukcropapi;

import com.mojang.logging.LogUtils;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.config.CropApiConfig;
import com.nsukcropapi.crop.CropDiscovery;
import com.nsukcropapi.nsuk.WorkOutcomes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import org.slf4j.Logger;

/**
 * NSUK 作物 API：
 * 统一接管 simukraft 农田盒的作物数据、作业流程与选择界面。
 * 各作物附属只需要在 FMLCommonSetupEvent 里调用 {@link CropRegistry#register} 注册数据。
 *
 * <p>所有附属共用一份配置（{@code config/nsukcropapi-common.toml}），
 * 界面入口是「NSUK 作物设置」：来源开关 + 自动发现。见 {@link CropApiConfig}。</p>
 */
@Mod(NsukCropApi.MODID)
public final class NsukCropApi {

    public static final String MODID = "nsukcropapi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NsukCropApi(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CropApiConfig.SPEC);
        modBus.addListener(this::onLoadComplete);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            // 自动发现放在最后：此时所有附属的 commonSetup 都已执行完，
            // 已注册的作物不会被自动发现覆盖。
            if (CropApiConfig.autoDiscovery()) {
                CropDiscovery.discover();
            }
            LOGGER.info("[nsukcropapi] {} crops registered by addons; five-stage takeover: {}",
                    CropRegistry.all().size(), WorkOutcomes.available() ? "enabled" : "disabled");
        });
    }
}

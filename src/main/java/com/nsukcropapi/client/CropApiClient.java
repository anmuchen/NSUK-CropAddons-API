package com.nsukcropapi.client;

import com.nsukcropapi.NsukCropApi;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端入口：给模组列表里的「配置」按钮挂上「NSUK 作物设置」。
 *
 * <p>各作物附属只要注册自己的 {@link IConfigScreenFactory} 指向同一个界面，
 * 点谁的「配置」都进同一份设置，不需要每个附属各写一套配置与界面。</p>
 */
@Mod(value = NsukCropApi.MODID, dist = Dist.CLIENT)
public final class CropApiClient {

    public CropApiClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new CropApiConfigScreen(parent));
    }
}

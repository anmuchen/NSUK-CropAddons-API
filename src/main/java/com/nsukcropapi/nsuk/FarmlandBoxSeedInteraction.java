package com.nsukcropapi.nsuk;

import com.nsukcropapi.NsukCropApi;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.FarmlandBoxes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 不依赖界面的兜底入口：手持已注册作物的种子右键农田盒即可指定作物。
 */
@EventBusSubscriber(modid = NsukCropApi.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class FarmlandBoxSeedInteraction {

    private FarmlandBoxSeedInteraction() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack stack = event.getItemStack();
        CropSpec spec = CropRegistry.bySeed(stack.getItem());
        if (spec == null) {
            return;
        }
        if (!FarmlandBoxes.isFarmlandBox(level.getBlockState(event.getPos()))) {
            return;
        }
        Player player = event.getEntity();
        if (!FarmlandBoxes.canManage(serverLevel, event.getPos(), player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.nsukcropapi.no_permission"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        boolean ok = FarmlandBoxes.setCrop(serverLevel, event.getPos(), spec.id());
        player.displayClientMessage(Component.translatable(
                ok ? "message.nsukcropapi.crop_set" : "message.nsukcropapi.crop_failed",
                Component.translatable(spec.displayNameKey())), true);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}

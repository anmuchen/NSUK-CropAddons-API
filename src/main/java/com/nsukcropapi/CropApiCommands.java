package com.nsukcropapi;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.FarmlandBoxes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 兜底命令：/nsukcrop farmland <作物 id>
 */
@EventBusSubscriber(modid = NsukCropApi.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CropApiCommands {

    private CropApiCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nsukcrop")
                .then(Commands.literal("farmland")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("crop", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (CropSpec spec : CropRegistry.all()) {
                                        builder.suggest(spec.id());
                                    }
                                    builder.suggest("wheat");
                                    return builder.buildFuture();
                                })
                                .executes(CropApiCommands::setFarmlandCrop))));
    }

    private static int setFarmlandCrop(CommandContext<CommandSourceStack> context) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(8.0D, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendFailure(Component.translatable("message.nsukcropapi.no_box"));
            return 0;
        }
        if (!FarmlandBoxes.isFarmlandBox(level.getBlockState(blockHit.getBlockPos()))) {
            context.getSource().sendFailure(Component.translatable("message.nsukcropapi.no_box"));
            return 0;
        }
        String input = StringArgumentType.getString(context, "crop");
        String cropId = CropRegistry.resolveId(input);
        if (cropId.isBlank()) {
            context.getSource().sendFailure(Component.translatable("message.nsukcropapi.unknown_crop", input));
            return 0;
        }
        if (FarmlandBoxes.setCrop(level, blockHit.getBlockPos(), cropId)) {
            context.getSource().sendSuccess(() -> Component.translatable("message.nsukcropapi.crop_set", cropId), true);
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("message.nsukcropapi.crop_failed", cropId));
        return 0;
    }
}

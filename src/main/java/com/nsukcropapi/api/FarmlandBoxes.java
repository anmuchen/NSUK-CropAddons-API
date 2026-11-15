package com.nsukcropapi.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 农田盒操作入口（反射调用 simukraft，旧版本缺少相关类时安全返回 false）。
 */
public final class FarmlandBoxes {

    private static final ResourceLocation BOX_ID =
            ResourceLocation.fromNamespaceAndPath("simukraft", "nsuk_farmland_box");

    private static volatile boolean resolved;
    private static volatile Method setCropMethod;

    private static volatile boolean permissionResolved;
    private static volatile Method cityIdMethod;
    private static volatile Method canManageMethod;

    private FarmlandBoxes() {
    }

    public static boolean isFarmlandBox(BlockState state) {
        if (state == null) {
            return false;
        }
        try {
            Block box = BuiltInRegistries.BLOCK.get(BOX_ID);
            return box != null && box != Blocks.AIR && state.is(box);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean setCrop(ServerLevel level, BlockPos pos, String cropId) {
        Method method = setCropMethod();
        if (method == null) {
            return false;
        }
        try {
            Object result = method.invoke(null, level, pos, cropId);
            return result instanceof Boolean ok && ok;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean canManage(ServerLevel level, BlockPos pos, UUID playerId) {
        resolvePermission();
        if (cityIdMethod == null || canManageMethod == null) {
            return false;
        }
        try {
            Object cityId = cityIdMethod.invoke(null, level, pos);
            if (!(cityId instanceof UUID uuid)) {
                return false;
            }
            Object result = canManageMethod.invoke(null, level, uuid, playerId);
            return result instanceof Boolean allowed && allowed;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Method setCropMethod() {
        if (!resolved) {
            synchronized (FarmlandBoxes.class) {
                if (!resolved) {
                    try {
                        Class<?> service = Class.forName("common.cn.nsimu.simukraft.farmland.FarmlandBoxService");
                        setCropMethod = service.getMethod("setCrop", ServerLevel.class, BlockPos.class, String.class);
                    } catch (Throwable t) {
                        setCropMethod = null;
                    }
                    resolved = true;
                }
            }
        }
        return setCropMethod;
    }

    private static void resolvePermission() {
        if (!permissionResolved) {
            synchronized (FarmlandBoxes.class) {
                if (!permissionResolved) {
                    try {
                        Class<?> service = Class.forName("common.cn.nsimu.simukraft.farmland.FarmlandBoxService");
                        cityIdMethod = service.getMethod("cityIdFor", ServerLevel.class, BlockPos.class);
                        Class<?> cityService = Class.forName("common.cn.nsimu.simukraft.city.CityService");
                        canManageMethod = cityService.getMethod("canManageCity",
                                ServerLevel.class, UUID.class, UUID.class);
                    } catch (Throwable t) {
                        cityIdMethod = null;
                        canManageMethod = null;
                    }
                    permissionResolved = true;
                }
            }
        }
    }
}

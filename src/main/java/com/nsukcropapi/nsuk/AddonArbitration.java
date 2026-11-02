package com.nsukcropapi.nsuk;

import com.nsukcropapi.api.CropRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * 多个农田盒附属共存时的归属仲裁（旧附属仍各自 mixin，需要让位/清理）。
 */
public final class AddonArbitration {

    private AddonArbitration() {
    }

    public static String foreignCropId(Object data) {
        if (data == null) {
            return "";
        }
        for (Class<?> itf : data.getClass().getInterfaces()) {
            for (Method method : itf.getMethods()) {
                if (method.getParameterCount() != 0
                        || method.getReturnType() != String.class
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                if (name.startsWith("nsukcropapi$") || (!name.endsWith("CropId") && !name.contains("AddonCrop"))) {
                    continue;
                }
                try {
                    Object value = method.invoke(data);
                    if (value instanceof String id && !id.isBlank()) {
                        return id;
                    }
                } catch (Throwable ignored) {
                    // 忽略其它模组的接口异常
                }
            }
        }
        return "";
    }

    /** 别的附属记录了有效作物（且不是本 API 注册的作物 id）时，本 API 让位。 */
    public static boolean shouldYield(Object data) {
        String foreign = foreignCropId(data);
        return !foreign.isBlank() && !CropRegistry.isRegistered(foreign);
    }

    public static boolean clearForeignIds(Object data) {
        if (data == null) {
            return false;
        }
        boolean changed = false;
        for (Class<?> itf : data.getClass().getInterfaces()) {
            for (Method method : itf.getMethods()) {
                if (method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != String.class
                        || Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                if (name.startsWith("nsukcropapi$") || !name.endsWith("$setAddonCropId")) {
                    continue;
                }
                try {
                    method.invoke(data, "");
                    changed = true;
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
        }
        return changed;
    }
}

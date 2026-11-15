package com.nsukcropapi.nsuk;

import com.nsukcropapi.NsukCropApi;
import com.nsukcropapi.api.StageOutcome;

import java.lang.reflect.Field;

/**
 * {@link StageOutcome} → simukraft 内部的 {@code FarmlandWorkResult}。
 *
 * <p>宿主把该枚举声明成包内可见，源码里拿不到类型，只能按名字反射取常量。
 * 常量在首次使用时解析一次并缓存；解析不到时返回 {@code null}，调用方据此回落宿主原逻辑，
 * 不会因为版本差异直接崩掉。</p>
 */
public final class WorkOutcomes {

    private static final String RESULT_CLASS = "common.cn.nsimu.simukraft.farmland.FarmlandWorkResult";

    private static volatile boolean resolved;
    private static Object processed;
    private static Object skipped;
    private static Object waiting;

    private WorkOutcomes() {
    }

    /** 宿主枚举是否可用（启动时打一行日志，便于快速定位"接管没生效"）。 */
    public static boolean available() {
        resolve();
        return processed != null && skipped != null && waiting != null;
    }

    /** 返回宿主枚举常量；宿主版本不匹配时返回 {@code null}。 */
    public static Object of(StageOutcome outcome) {
        if (outcome == null) {
            return null;
        }
        resolve();
        return switch (outcome) {
            case PROCESSED -> processed;
            case SKIPPED -> skipped;
            case WAITING_MATERIAL -> waiting;
        };
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (WorkOutcomes.class) {
            if (resolved) {
                return;
            }
            processed = constant("PROCESSED");
            skipped = constant("SKIPPED");
            waiting = constant("WAITING_SEED");
            resolved = true;
            if (processed == null || skipped == null || waiting == null) {
                NsukCropApi.LOGGER.error("[nsukcropapi] 无法读取 simukraft 的 FarmlandWorkResult（PROCESSED/SKIPPED/WAITING_SEED），"
                        + "五阶段接管已降级为宿主默认逻辑");
            }
        }
    }

    private static Object constant(String name) {
        try {
            Class<?> type = Class.forName(RESULT_CLASS);
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable t) {
            return null;
        }
    }
}

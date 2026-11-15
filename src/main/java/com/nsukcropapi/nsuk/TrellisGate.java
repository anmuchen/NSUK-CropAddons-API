package com.nsukcropapi.nsuk;

import com.nsukcropapi.api.CropSpec;
import com.nsukcropapi.api.CropWork;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 藤架几何的公共判定与节流。
 *
 * <p>放在 {@code nsuk} 包而不是 mixin 包，是因为 mixin 包只允许放 mixin 本身——
 * 混入辅助类会在类加载阶段被 Mixin 当成未被配置的 mixin 而报错。</p>
 *
 * <p>计时状态放在这里而不是 mixin 里，是因为 mixin 的静态字段会并入目标类，
 * 生命周期与目标类绑定，放在普通类里语义更清楚、也便于单独测试。</p>
 */
public final class TrellisGate {

    /** 先让雇农自己走这么久（tick），走不到才考虑传送。 */
    private static final long WALK_GRACE_TICKS = 40L;

    /** 两次传送之间的最小间隔（tick）。 */
    private static final long TELEPORT_COOLDOWN_TICKS = 10L;

    /** 每个雇农的 [盒位置, 开始尝试时刻, 上次传送时刻]。 */
    private static final Map<UUID, long[]> REACH_STATE = new ConcurrentHashMap<>();

    private static volatile Method cropPosAccessor;

    private TrellisGate() {
    }

    /** 当前 tick 的作物是否声明了藤架几何（藤架 / 木格架 / 藤茎堆）。 */
    public static boolean active() {
        CropSpec spec = CropContext.current();
        if (spec == null) {
            return false;
        }
        CropWork work = spec.workOrDefault();
        return work != null && Boolean.TRUE.equals(work.usesTrellisWork(spec));
    }

    /**
     * 此刻是否允许把该雇农传送到落脚点。
     *
     * <p>规则：换了农田盒就重新计时；前 {@value #WALK_GRACE_TICKS} tick 一律不传，
     * 之后每次传送后冷却 {@value #TELEPORT_COOLDOWN_TICKS} tick。</p>
     */
    public static boolean allowTeleport(UUID citizenId, BlockPos boxPos, long gameTime) {
        if (citizenId == null || boxPos == null) {
            return false;
        }
        long boxKey = boxPos.asLong();
        long[] state = REACH_STATE.get(citizenId);
        if (state == null || state[0] != boxKey) {
            state = new long[]{boxKey, gameTime, -1000L};
            REACH_STATE.put(citizenId, state);
        }
        return gameTime - state[1] >= WALK_GRACE_TICKS && gameTime - state[2] >= TELEPORT_COOLDOWN_TICKS;
    }

    /** 记录一次传送时刻，供下一次冷却判定使用。 */
    public static void markTeleported(UUID citizenId, long gameTime) {
        long[] state = citizenId == null ? null : REACH_STATE.get(citizenId);
        if (state != null) {
            state[2] = gameTime;
        }
    }

    /** 世界卸载时清掉计时，避免跨存档串味。 */
    public static void clear() {
        REACH_STATE.clear();
    }

    /**
     * 从宿主的作业目标对象里读作物坐标。
     *
     * <p>{@code FarmlandWorkTarget} 是包内可见的 record，API 编译期拿不到它的类型，
     * 只能反射调用 {@code cropPos()}。取不到时返回 {@code null}，调用方按"不接管"处理。</p>
     */
    public static BlockPos cropPosOf(Object target) {
        if (target == null) {
            return null;
        }
        try {
            Method accessor = cropPosAccessor;
            if (accessor == null) {
                accessor = target.getClass().getDeclaredMethod("cropPos");
                accessor.setAccessible(true);
                cropPosAccessor = accessor;
            }
            return (BlockPos) accessor.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

package com.nsukcropapi.api;

import com.nsukcropapi.crop.BoxContext;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxData;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxManager;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 农田盒上下文的公共入口。
 *
 * <p>作业期间宿主逐个 tick 处理农田盒，附属若要知道"现在处理的是哪个盒、圈地多大"，
 * 在 {@link CropWork#onTickStart} 里 {@link #push(Object)} 一次、在
 * {@link CropWork#onTickEnd} 里 {@link #pop()} 一次即可，不必自己维护线程本地栈。</p>
 *
 * <p>上下文按线程隔离：服务端主线程与各维度线程互不影响。</p>
 */
public final class Boxes {

    private Boxes() {
    }

    /**
     * 压入当前处理的农田盒。
     *
     * @param data {@link CropWorkContext#data()} 原样传入即可；null 或类型不符时按"无盒"处理
     */
    public static void push(Object data) {
        BoxContext.push(data instanceof FarmlandBoxData box ? box : null);
    }

    public static void pop() {
        BoxContext.pop();
    }

    public static void clear() {
        BoxContext.clear();
    }

    /** 当前正在处理的农田盒；不在作业上下文中时为空。 */
    public static Optional<BoxView> current() {
        return BoxContext.currentBox().map(BoxView::of);
    }

    /** 当前世界里的全部农田盒（供诊断命令、批量统计使用）。 */
    public static List<BoxView> all(ServerLevel level) {
        if (level == null) {
            return List.of();
        }
        try {
            List<FarmlandBoxData> raw = FarmlandBoxManager.get(level).all();
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            List<BoxView> views = new ArrayList<>(raw.size());
            for (FarmlandBoxData data : raw) {
                BoxView view = BoxView.of(data);
                if (view != null) {
                    views.add(view);
                }
            }
            return views;
        } catch (Throwable ignored) {
            return List.of();
        }
    }
}

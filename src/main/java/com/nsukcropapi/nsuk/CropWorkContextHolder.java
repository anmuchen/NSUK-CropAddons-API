package com.nsukcropapi.nsuk;

import com.nsukcropapi.api.CropWorkContext;

/**
 * 作业上下文（线程本地）。由 tickBox 包装方法压入，供 FarmCrop 委托方法读取
 * 箱子列表 / 农田盒数据等运行期信息。
 */
public final class CropWorkContextHolder {

    private static final ThreadLocal<CropWorkContext> CURRENT = new ThreadLocal<>();

    private CropWorkContextHolder() {
    }

    public static void push(CropWorkContext context) {
        CURRENT.set(context);
    }

    public static CropWorkContext current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}

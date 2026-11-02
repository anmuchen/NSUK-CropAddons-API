package com.nsukcropapi.nsuk;

import com.nsukcropapi.api.CropSpec;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 当前正在处理的作物（线程本地栈），供 FarmCrop 占位枚举的委托方法使用。
 */
public final class CropContext {

    private static final ThreadLocal<Deque<CropSpec>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private CropContext() {
    }

    public static void push(CropSpec spec) {
        if (spec != null) {
            STACK.get().push(spec);
        }
    }

    public static void pop() {
        Deque<CropSpec> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static CropSpec current() {
        Deque<CropSpec> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    public static void clear() {
        STACK.remove();
    }
}

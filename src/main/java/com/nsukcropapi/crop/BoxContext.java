package com.nsukcropapi.crop;

import com.nsukcropapi.nsuk.FarmlandBoxDataExt;
import com.nsukcropapi.NsukCropApi;
import common.cn.nsimu.simukraft.farmland.FarmlandBoxData;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 当前正在处理的农田盒（线程本地栈）。
 *
 * <p>作物 id 从 NSUK 作物 API 的存档字段读（迁移后本模组不再自己记 id），
 * 供 {@link TrellisCropWork} 的站位/几何计算与状态文案使用。</p>
 */
public final class BoxContext {
   private static final ThreadLocal<Deque<BoxContext.Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

   private BoxContext() {
   }

   public static void push(FarmlandBoxData data) {
      STACK.get().push(new BoxContext.Frame(resolveOrMissing(data), data));
   }

   public static void pop() {
      Deque<BoxContext.Frame> stack = STACK.get();
      if (!stack.isEmpty()) {
         stack.pop();
      }

      if (stack.isEmpty()) {
         STACK.remove();
      }
   }

   public static void clear() {
      STACK.remove();
   }

   public static Optional<ResolvedCrop> current() {
      BoxContext.Frame frame = peek();
      return frame != null && frame.crop != null && !frame.crop.isMissing() ? Optional.of(frame.crop) : Optional.empty();
   }

   public static Optional<FarmlandBoxData> currentBox() {
      BoxContext.Frame frame = peek();
      return frame != null && frame.box != null ? Optional.of(frame.box) : Optional.empty();
   }

   private static BoxContext.Frame peek() {
      Deque<BoxContext.Frame> stack = STACK.get();
      return stack.isEmpty() ? null : stack.peek();
   }

   private static ResolvedCrop resolveOrMissing(FarmlandBoxData data) {
      if (data == null) {
         return ResolvedCrop.missing();
      }
      try {
         String cropId = ((FarmlandBoxDataExt)(Object)data).nsukcropapi$getCropId();
         return cropId != null && !cropId.isBlank() ? CropCatalog.resolve(cropId).orElse(ResolvedCrop.missing()) : ResolvedCrop.missing();
      } catch (Exception var21) {
         NsukCropApi.LOGGER.error("Failed to bind delight crop context for farmland box", var21);
         return ResolvedCrop.missing();
      }
   }

   private static record Frame(ResolvedCrop crop, FarmlandBoxData box) {
   }
}

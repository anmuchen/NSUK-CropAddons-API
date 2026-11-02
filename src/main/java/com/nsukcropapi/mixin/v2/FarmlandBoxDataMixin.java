package com.nsukcropapi.mixin.v2;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.nsukcropapi.api.CropRegistry;
import com.nsukcropapi.nsuk.AddonArbitration;
import com.nsukcropapi.nsuk.FarmlandBoxDataExt;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 农田盒数据扩展：记录 API 作物 id，并与旧附属做归属仲裁。
 */
@Mixin(value = FarmlandBoxData.class, remap = false)
public abstract class FarmlandBoxDataMixin implements FarmlandBoxDataExt {

    private static final String API_TAG = "NsukCropApiCrop";

    @Unique
    private String nsukcropapi$cropId = "";

    @Override
    public String nsukcropapi$getCropId() {
        return this.nsukcropapi$cropId == null ? "" : this.nsukcropapi$cropId;
    }

    @Override
    public void nsukcropapi$setCropId(String cropId) {
        this.nsukcropapi$cropId = cropId == null ? "" : cropId;
    }

    @WrapMethod(method = "toTag")
    private CompoundTag nsukcropapi$writeTag(Operation<CompoundTag> original) {
        CompoundTag tag = original.call();
        String cropId = this.nsukcropapi$getCropId();
        if (tag == null || cropId.isBlank()) {
            return tag;
        }
        if (AddonArbitration.shouldYield((Object) this)) {
            this.nsukcropapi$setCropId("");
            return tag;
        }
        tag.putString(API_TAG, cropId);
        tag.putString("Crop", cropId);
        return tag;
    }

    @WrapMethod(method = "fromTag")
    private static FarmlandBoxData nsukcropapi$readTag(CompoundTag tag, Operation<FarmlandBoxData> original) {
        FarmlandBoxData data = original.call(tag);
        if (data == null || tag == null) {
            return data;
        }
        String shared = tag.contains("Crop") ? tag.getString("Crop") : "";
        String own = tag.contains(API_TAG) ? tag.getString(API_TAG) : "";
        if (own.isBlank() && CropRegistry.isRegistered(shared)) {
            own = shared;
        }
        if (own.isBlank() || !CropRegistry.isRegistered(own)) {
            return data;
        }
        // 共享字段属于其它附属：不抢
        if (!shared.isBlank() && !CropRegistry.isRegistered(shared) && !shared.equals(own)) {
            return data;
        }
        data.setCrop(FarmCrop.WHEAT);
        ((FarmlandBoxDataExt) (Object) data).nsukcropapi$setCropId(own);
        return data;
    }
}

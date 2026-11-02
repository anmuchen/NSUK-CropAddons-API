package com.nsukcropapi.nsuk;

/**
 * 由 Mixin 织入 {@code FarmlandBoxData}，记录农田盒当前的 API 作物 id。
 */
public interface FarmlandBoxDataExt {

    String nsukcropapi$getCropId();

    void nsukcropapi$setCropId(String cropId);
}

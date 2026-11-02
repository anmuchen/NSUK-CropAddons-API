package com.nsukcropapi.api;

import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 作物方块判定。默认按原版 {@link CropBlock}（age 0..7）处理。
 */
public final class CropRules {

    private CropRules() {
    }

    public static BlockState plantState(CropSpec spec) {
        return spec.plantBlock().defaultBlockState();
    }

    public static boolean isOwnPlant(CropSpec spec, BlockState state) {
        return state != null && state.is(spec.plantBlock());
    }

    public static boolean isMatureFull(CropSpec spec, BlockState state) {
        return isOwnPlant(spec, state)
                && state.hasProperty(CropBlock.AGE)
                && state.getValue(CropBlock.AGE) >= CropBlock.MAX_AGE;
    }
}

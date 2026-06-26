package com.adore.convenientcrafting.config;

import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.conditions.ICondition;

public final class CategoryBagsEnabledCondition implements ICondition {
    public static final CategoryBagsEnabledCondition INSTANCE = new CategoryBagsEnabledCondition();
    public static final MapCodec<CategoryBagsEnabledCondition> CODEC = MapCodec.unit(INSTANCE).stable();

    private CategoryBagsEnabledCondition() {
    }

    @Override
    public boolean test(IContext context) {
        return Config.ENABLE_CATEGORY_BAGS.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}

package com.adore.convenientcrafting.registry;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.config.CategoryBagsEnabledCondition;
import com.mojang.serialization.MapCodec;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModConditions {
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, ConvenientCrafting.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<CategoryBagsEnabledCondition>> CATEGORY_BAGS_ENABLED =
            CONDITION_CODECS.register("category_bags_enabled", () -> CategoryBagsEnabledCondition.CODEC);

    private ModConditions() {
    }

    public static void register(IEventBus modEventBus) {
        CONDITION_CODECS.register(modEventBus);
    }
}

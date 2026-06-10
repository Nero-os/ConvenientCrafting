package com.adore.convenientcrafting.registry;

import com.adore.convenientcrafting.ConvenientCrafting;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组物品注册入口。
 *
 * <p>1.4.0 计划新增的收纳袋类物品可以集中注册在这里，避免主入口类继续膨胀。</p>
 */
public final class ModItems {
    /**
     * 物品延迟注册器。
     */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ConvenientCrafting.MODID);

    private ModItems() {
    }

    /**
     * 注册模组物品。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

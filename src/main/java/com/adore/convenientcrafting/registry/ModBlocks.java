package com.adore.convenientcrafting.registry;

import com.adore.convenientcrafting.ConvenientCrafting;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组方块注册入口。
 *
 * <p>当前版本暂未添加自定义方块，保留独立注册类是为了让后续版本新增方块时
 * 不再把注册逻辑塞回主入口类。</p>
 */
public final class ModBlocks {
    /**
     * 方块延迟注册器。
     */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ConvenientCrafting.MODID);

    private ModBlocks() {
    }

    /**
     * 注册模组方块。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

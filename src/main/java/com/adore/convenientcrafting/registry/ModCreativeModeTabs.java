package com.adore.convenientcrafting.registry;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.config.Config;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组创造模式标签注册入口。
 */
public final class ModCreativeModeTabs {
    /**
     * 创造模式标签延迟注册器。
     */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ConvenientCrafting.MODID);

    /**
     * 便捷合成创造模式标签。
     *
     * <p>目前尚未加入模组物品，因此暂时使用原版工作台作为图标。
     * 1.4.0 新增收纳袋类物品后，可以把标签图标和展示内容切换到模组物品。</p>
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CONVENIENT_CRAFTING_TAB =
            CREATIVE_MODE_TABS.register("convenient_crafting", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.convenientcrafting"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> Config.ENABLE_CATEGORY_BAGS.get() ? ModItems.SEED_BAG.toStack() : new ItemStack(Items.CRAFTING_TABLE))
                    .displayItems((parameters, output) -> {
                        if (!Config.ENABLE_CATEGORY_BAGS.get()) {
                            return;
                        }

                        output.accept(ModItems.SEED_BAG.get());
                        output.accept(ModItems.DYE_BAG.get());
                        output.accept(ModItems.MINERAL_BAG.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }

    /**
     * 注册模组创造模式标签。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}

package com.adore.convenientcrafting.registry;

import com.adore.convenientcrafting.ConvenientCrafting;
import com.adore.convenientcrafting.item.CategorizedBagItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.registries.DeferredItem;
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
    public static final TagKey<Item> SEEDS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "seeds"));
    public static final TagKey<Item> MINERALS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(ConvenientCrafting.MODID, "minerals"));

    public static final DeferredItem<Item> SEED_BAG = ITEMS.register(
            "seed_bag",
            () -> new CategorizedBagItem(bagProperties(), SEEDS));

    public static final DeferredItem<Item> DYE_BAG = ITEMS.register(
            "dye_bag",
            () -> new CategorizedBagItem(bagProperties(), Tags.Items.DYES));

    public static final DeferredItem<Item> MINERAL_BAG = ITEMS.register(
            "mineral_bag",
            () -> new CategorizedBagItem(bagProperties(), MINERALS));

    private ModItems() {
    }

    private static Item.Properties bagProperties() {
        return new Item.Properties()
                .stacksTo(1)
                .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
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

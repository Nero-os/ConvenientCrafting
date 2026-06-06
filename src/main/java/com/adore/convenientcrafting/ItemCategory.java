package com.adore.convenientcrafting;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public enum ItemCategory {
    TOOLS("Tools"),
    BUILDING_BLOCKS("Building Blocks"),
    FOOD("Food"),
    REDSTONE("Redstone"),
    LOOT("Loot"),
    COMBAT("Combat"),
    MISC("Misc");

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ItemCategory categorizeItem(Item item) {
        if (item instanceof PickaxeItem ||
            item instanceof SwordItem ||
            item instanceof ShovelItem ||
            item instanceof AxeItem ||
            item instanceof HoeItem) {
            return TOOLS;
        }

        if (item instanceof BowItem ||
            item instanceof CrossbowItem ||
            item instanceof ArmorItem ||
            item instanceof ShieldItem) {
            return COMBAT;
        }

        ItemStack tempStack = new ItemStack(item);
        if (tempStack.get(DataComponents.FOOD) != null) {
            return FOOD;
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();

            if (block == Blocks.REDSTONE_BLOCK ||
                block == Blocks.REDSTONE_WIRE ||
                block == Blocks.REPEATER ||
                block == Blocks.COMPARATOR ||
                block == Blocks.REDSTONE_TORCH ||
                block == Blocks.LEVER ||
                block == Blocks.STONE_BUTTON ||
                block == Blocks.OAK_BUTTON) {
                return REDSTONE;
            }

            return BUILDING_BLOCKS;
        }

        return MISC;
    }

    public int getCategoryPriority() {
        return switch (this) {
            case TOOLS -> 0;
            case COMBAT -> 1;
            case FOOD -> 2;
            case REDSTONE -> 3;
            case BUILDING_BLOCKS -> 4;
            case LOOT -> 5;
            case MISC -> 6;
        };
    }
}

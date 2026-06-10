package com.adore.convenientcrafting.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 物品整理时使用的粗略分类。
 *
 * <p>分类主要服务于背包排序，不追求覆盖所有 Minecraft 物品细节；
 * 未命中特定规则的物品会被归入 {@link #MISC}。</p>
 */
public enum ItemCategory {
    /**
     * 常用工具类，如镐、剑、铲、斧、锄。
     */
    TOOLS("Tools"),

    /**
     * 可放置的普通建筑方块。
     */
    BUILDING_BLOCKS("Building Blocks"),

    /**
     * 带食物组件的物品。
     */
    FOOD("Food"),

    /**
     * 红石相关方块或物品。
     */
    REDSTONE("Redstone"),

    /**
     * 预留的战利品分类。
     */
    LOOT("Loot"),

    /**
     * 战斗装备，如弓、弩、护甲、盾牌。
     */
    COMBAT("Combat"),

    /**
     * 其他未命中特定规则的物品。
     */
    MISC("Misc");

    private final String displayName;

    ItemCategory(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取用于界面展示的分类名称。
     *
     * @return 分类展示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据物品类型和基础组件推断排序分类。
     *
     * @param item 要分类的物品
     * @return 推断出的物品分类
     */
    public static ItemCategory categorizeItem(Item item) {
        // 工具和战斗装备先判断，避免部分特殊物品后续被宽泛规则吞掉。
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

        // 食物使用 DataComponents.FOOD 判断，可兼容原版和模组添加的可食用物品。
        ItemStack tempStack = new ItemStack(item);
        if (tempStack.get(DataComponents.FOOD) != null) {
            return FOOD;
        }

        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();

            // 常见红石组件优先归类为红石，而不是普通建筑方块。
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

    /**
     * 获取分类排序优先级。
     *
     * @return 数值越小越靠前
     */
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

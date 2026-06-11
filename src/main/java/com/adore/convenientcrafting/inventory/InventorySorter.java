package com.adore.convenientcrafting.inventory;

import com.adore.convenientcrafting.item.CategorizedBagItem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 背包与容器整理工具类。
 *
 * <p>该类会先把容器内相同物品汇总为总数量，再按物品分类、常用工具优先级、
 * 方块注册名或数量进行排序，最后按最大堆叠数重新拆分并写回槽位。</p>
 */
public class InventorySorter {
    private static final int PLAYER_INVENTORY_SIZE = 36;
    private static final int HOTBAR_START = 0;
    private static final int HOTBAR_END = 9;
    private static final int MAIN_INVENTORY_START = 9;
    private static final int MAIN_INVENTORY_END = PLAYER_INVENTORY_SIZE;

    /**
     * 整理玩家背包。
     *
     * @param inventory 要整理的玩家背包；为 {@code null} 时直接忽略
     */
    public static void sortInventory(Inventory inventory) {
        if (inventory == null) return;

        compactMatchingItemsIntoBags(inventory, PLAYER_INVENTORY_SIZE);

        Map<String, StackBucket> itemCounts = new LinkedHashMap<>();

        // 第一步：只记录物品类型和总数量，避免原始槽位顺序影响后续排序结果。
        for (int i = 0; i < PLAYER_INVENTORY_SIZE; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                addToBuckets(itemCounts, stack);
            }
        }

        // 第二步：清空原槽位，后续按排序后的顺序重新写入。
        for (int i = 0; i < PLAYER_INVENTORY_SIZE; i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }

        List<StackBucket> sortedItems = itemCounts.values().stream()
                .sorted(InventorySorter::compareStacks)
                .collect(Collectors.toList());

        distributeItems(inventory, sortedItems, true, PLAYER_INVENTORY_SIZE);
    }

    /**
     * 整理任意实现了 {@link Container} 的容器。
     *
     * @param container 要整理的容器；为 {@code null} 时直接忽略
     */
    public static void sortContainer(Container container) {
        if (container == null) return;

        compactMatchingItemsIntoBags(container, container.getContainerSize());

        Map<String, StackBucket> itemCounts = new LinkedHashMap<>();

        // 与玩家背包逻辑一致：先汇总数量，避免直接移动物品时出现槽位覆盖。
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                addToBuckets(itemCounts, stack);
            }
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        List<StackBucket> sortedItems = itemCounts.values().stream()
                .sorted(InventorySorter::compareStacks)
                .collect(Collectors.toList());

        distributeItems(container, sortedItems, false, container.getContainerSize());
    }

    /**
     * 比较两个物品条目的排序顺序。
     *
     * <p>排序规则从高到低依次为：分类优先级、常用工具优先、建筑方块注册名、
     * 物品数量降序。前面的规则相同才会进入下一条规则。</p>
     *
     * @param a 第一个物品及其总数量
     * @param b 第二个物品及其总数量
     * @return 小于 0 表示 {@code a} 排在前面，大于 0 表示 {@code b} 排在前面
     */
    private static int compareStacks(StackBucket a, StackBucket b) {
        Item itemA = a.prototype.getItem();
        Item itemB = b.prototype.getItem();

        ItemCategory catA = ItemCategory.categorizeItem(itemA);
        ItemCategory catB = ItemCategory.categorizeItem(itemB);

        // 先按大的功能分类排序，让工具、战斗、食物等区域更稳定。
        int categoryCompare = Integer.compare(catA.getCategoryPriority(), catB.getCategoryPriority());
        if (categoryCompare != 0) {
            return categoryCompare;
        }

        // 镐和剑是玩家最常频繁切换的工具，同分类内优先放到更靠前的位置。
        if (isFrequentTool(itemA) && !isFrequentTool(itemB)) {
            return -1;
        }
        if (!isFrequentTool(itemA) && isFrequentTool(itemB)) {
            return 1;
        }

        if (catA == ItemCategory.BUILDING_BLOCKS && catB == ItemCategory.BUILDING_BLOCKS) {
            ResourceLocation locA = BuiltInRegistries.ITEM.getKey(itemA);
            ResourceLocation locB = BuiltInRegistries.ITEM.getKey(itemB);
            if (locA != null && locB != null) {
                return locA.getPath().compareTo(locB.getPath());
            }
        }

        return Integer.compare(b.count, a.count);
    }

    /**
     * 判断物品是否属于常用工具。
     *
     * @param item 要判断的物品
     * @return 如果是镐或剑则返回 {@code true}
     */
    private static boolean isFrequentTool(Item item) {
        return item instanceof PickaxeItem || item instanceof SwordItem;
    }

    /**
     * 将排序后的物品重新分发回容器槽位。
     *
     * <p>玩家背包会额外优先把常用工具放入快捷栏；普通容器则从前往后填充。
     * 每种物品会按最大堆叠数拆成多个 {@link ItemStack}，直到该物品总数量全部写回。</p>
     *
     * @param container 要写回的容器
     * @param sortedItems 已按目标规则排序的物品及总数量列表
     * @param isPlayerInventory 是否按玩家背包的快捷栏偏好处理
     * @param <T> 容器类型
     */
    private static <T extends Container> void distributeItems(
            T container, List<StackBucket> sortedItems, boolean isPlayerInventory, int sortableSlots) {
        int currentSlot = 0;

        for (StackBucket entry : sortedItems) {
            Item item = entry.prototype.getItem();
            int totalCount = entry.count;
            int maxStackSize = entry.prototype.getMaxStackSize();

            boolean frequentTool = isFrequentTool(item);

            while (totalCount > 0) {
                int stackSize = Math.min(totalCount, maxStackSize);
                ItemStack newStack = entry.prototype.copyWithCount(stackSize);

                int preferredSlot = -1;

                if (frequentTool && isPlayerInventory) {
                    // 常用工具先抢占快捷栏空位，方便整理后立刻使用。
                    for (int slot = HOTBAR_START; slot < HOTBAR_END; slot++) {
                        if (container.getItem(slot).isEmpty()) {
                            preferredSlot = slot;
                            break;
                        }
                    }

                    if (preferredSlot == -1) {
                        // 快捷栏已满时，再退回玩家主背包区域。
                        for (int slot = MAIN_INVENTORY_START; slot < MAIN_INVENTORY_END; slot++) {
                            if (container.getItem(slot).isEmpty()) {
                                preferredSlot = slot;
                                break;
                            }
                        }
                    }
                }

                if (preferredSlot == -1) {
                    // 普通物品从当前扫描位置继续查找，避免每次都从 0 开始造成额外重复扫描。
                    for (int slot = currentSlot; slot < sortableSlots; slot++) {
                        if (container.getItem(slot).isEmpty()) {
                            preferredSlot = slot;
                            currentSlot = slot + 1;
                            break;
                        }
                    }
                }

                if (preferredSlot == -1) {
                    // 如果前面的偏好扫描都没找到，再全容器兜底扫描一次。
                    for (int slot = 0; slot < sortableSlots; slot++) {
                        if (container.getItem(slot).isEmpty()) {
                            preferredSlot = slot;
                            break;
                        }
                    }
                }

                if (preferredSlot != -1) {
                    container.setItem(preferredSlot, newStack);
                }

                totalCount -= stackSize;
            }
        }
    }

    private static void compactMatchingItemsIntoBags(Container container, int sortableSlots) {
        List<BagSlot> bags = new ArrayList<>();
        for (int i = 0; i < sortableSlots; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() instanceof CategorizedBagItem) {
                bags.add(new BagSlot(i, stack));
            }
        }

        if (bags.isEmpty()) {
            return;
        }

        for (int i = 0; i < sortableSlots; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || stack.getItem() instanceof CategorizedBagItem) {
                continue;
            }

            for (BagSlot bag : bags) {
                if (stack.isEmpty()) {
                    break;
                }
                if (bag.stack().getItem() instanceof CategorizedBagItem bagItem && bagItem.insert(bag.stack(), stack) > 0) {
                    container.setItem(bag.slotIndex(), bag.stack());
                }
            }

            container.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
    }

    private static void addToBuckets(Map<String, StackBucket> buckets, ItemStack stack) {
        String key = buildStackKey(stack);
        StackBucket bucket = buckets.get(key);
        if (bucket == null) {
            buckets.put(key, new StackBucket(stack.copyWithCount(1), stack.getCount()));
        } else {
            bucket.count += stack.getCount();
        }
    }

    private static String buildStackKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (itemId != null ? itemId.toString() : "unknown:" + stack.getItem()) + stack.getComponentsPatch();
    }

    private static class StackBucket {
        private final ItemStack prototype;
        private int count;

        private StackBucket(ItemStack prototype, int count) {
            this.prototype = prototype;
            this.count = count;
        }
    }

    private record BagSlot(int slotIndex, ItemStack stack) {
    }
}

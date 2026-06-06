package com.adore.convenientcrafting;

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

public class InventorySorter {

    public static void sortInventory(Inventory inventory) {
        if (inventory == null) return;

        Map<Item, Integer> itemCounts = new HashMap<>();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                int count = itemCounts.getOrDefault(item, 0);
                itemCounts.put(item, count + stack.getCount());
            }
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, ItemStack.EMPTY);
        }

        List<Map.Entry<Item, Integer>> sortedItems = itemCounts.entrySet().stream()
                .sorted(InventorySorter::compareItems)
                .collect(Collectors.toList());

        distributeItems(inventory, sortedItems, true);
    }

    public static void sortContainer(Container container) {
        if (container == null) return;

        Map<Item, Integer> itemCounts = new HashMap<>();

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                int count = itemCounts.getOrDefault(item, 0);
                itemCounts.put(item, count + stack.getCount());
            }
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        List<Map.Entry<Item, Integer>> sortedItems = itemCounts.entrySet().stream()
                .sorted(InventorySorter::compareItems)
                .collect(Collectors.toList());

        distributeItems(container, sortedItems, false);
    }

    private static int compareItems(Map.Entry<Item, Integer> a, Map.Entry<Item, Integer> b) {
        Item itemA = a.getKey();
        Item itemB = b.getKey();

        ItemCategory catA = ItemCategory.categorizeItem(itemA);
        ItemCategory catB = ItemCategory.categorizeItem(itemB);

        int categoryCompare = Integer.compare(catA.getCategoryPriority(), catB.getCategoryPriority());
        if (categoryCompare != 0) {
            return categoryCompare;
        }

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

        return Integer.compare(b.getValue(), a.getValue());
    }

    private static boolean isFrequentTool(Item item) {
        return item instanceof PickaxeItem || item instanceof SwordItem;
    }

    private static <T extends Container> void distributeItems(T container, List<Map.Entry<Item, Integer>> sortedItems, boolean isPlayerInventory) {
        int hotbarStart = 36;
        int hotbarEnd = 45;
        int mainInventoryStart = 9;
        int mainInventoryEnd = 36;

        int currentSlot = 0;

        for (Map.Entry<Item, Integer> entry : sortedItems) {
            Item item = entry.getKey();
            int totalCount = entry.getValue();
            ItemStack tempStack = new ItemStack(item);
            int maxStackSize = item.getMaxStackSize(tempStack);

            boolean frequentTool = isFrequentTool(item);

            while (totalCount > 0) {
                int stackSize = Math.min(totalCount, maxStackSize);
                ItemStack newStack = new ItemStack(item, stackSize);

                int preferredSlot = -1;

                if (frequentTool && isPlayerInventory) {
                    for (int slot = hotbarStart; slot < hotbarEnd; slot++) {
                        if (container.getItem(slot).isEmpty()) {
                            preferredSlot = slot;
                            break;
                        }
                    }

                    if (preferredSlot == -1) {
                        for (int slot = mainInventoryStart; slot < mainInventoryEnd; slot++) {
                            if (container.getItem(slot).isEmpty()) {
                                preferredSlot = slot;
                                break;
                            }
                        }
                    }
                }

                if (preferredSlot == -1) {
                    for (int slot = currentSlot; slot < container.getContainerSize(); slot++) {
                        if (container.getItem(slot).isEmpty()) {
                            preferredSlot = slot;
                            currentSlot = slot + 1;
                            break;
                        }
                    }
                }

                if (preferredSlot == -1) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
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
}

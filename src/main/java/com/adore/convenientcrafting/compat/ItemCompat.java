package com.adore.convenientcrafting.compat;

import net.minecraft.world.item.ItemStack;

/** Container and crafting remainder APIs changed names in 1.21.4. */
public final class ItemCompat {
    private ItemCompat() {}

    public static boolean canFitInsideContainer(ItemStack stack) {
        //? if >=1.21.4 {
        /*return stack.getItem().canFitInsideContainerItems();
        *///?} else {
        return stack.canFitInsideContainerItems();
        //?}
    }

    public static ItemStack craftingRemainder(ItemStack stack) {
        //? if >=1.21.4 {
        /*return stack.getCraftingRemainder();
        *///?} else {
        return stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
        //?}
    }
}

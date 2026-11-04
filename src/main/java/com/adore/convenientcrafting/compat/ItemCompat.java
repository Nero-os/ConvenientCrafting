package com.adore.convenientcrafting.compat;

import net.minecraft.world.item.ItemStack;

/** Container and crafting remainder APIs changed names in 1.21.2. */
public final class ItemCompat {
    private ItemCompat() {}

    public static net.minecraft.world.entity.player.Inventory emptyInventory(net.minecraft.world.entity.player.Player player) {
        //? if >=26.1 {
        /*return new net.minecraft.world.entity.player.Inventory(player, new net.minecraft.world.entity.EntityEquipment());
        *///?} else {
        return new net.minecraft.world.entity.player.Inventory(player);
        //?}
    }

    public static boolean canFitInsideContainer(ItemStack stack) {
        //? if >=1.21.2 {
        /*return stack.getItem().canFitInsideContainerItems();
        *///?} else {
        return stack.canFitInsideContainerItems();
        //?}
    }

    public static ItemStack craftingRemainder(ItemStack stack) {
        //? if >=26.1 {
        /*var remainder = stack.getCraftingRemainder();
        return remainder == null ? ItemStack.EMPTY : remainder.create();
        *///?} elif >=1.21.2 {
        /*return stack.getCraftingRemainder();
        *///?} else {
        return stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
        //?}
    }
}

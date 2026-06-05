package com.adore.smartbundle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record CraftRecipePacket(ResourceLocation recipeId) implements CustomPacketPayload {

    public static final Type<CraftRecipePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(SmartBundle.MODID, "craft_recipe")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRecipePacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            CraftRecipePacket::recipeId,
            CraftRecipePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(CraftRecipePacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                craftRecipe(player, message.recipeId());
            }
        });
    }

    private static void craftRecipe(ServerPlayer player, ResourceLocation recipeId) {
        var server = player.getServer();
        if (server == null) return;

        var optional = server.getRecipeManager().byKey(recipeId);
        if (optional.isEmpty()) return;

        var holder = optional.get();
        if (!(holder.value() instanceof CraftingRecipe recipe)) return;

        ItemStack result = recipe.getResultItem(server.registryAccess());
        if (result.isEmpty()) return;

        Inventory inventory = player.getInventory();

        // Check if player has enough ingredients
        if (!hasIngredients(inventory, recipe)) return;

        // Check if there's enough space in inventory for the result
        if (!hasSpaceForResult(inventory, result)) return;

        // Consume ingredients
        consumeIngredients(inventory, recipe);

        // Give result
        inventory.add(result.copy());

        // Broadcast changes
        player.containerMenu.broadcastChanges();
    }

    private static boolean hasIngredients(Inventory inventory, CraftingRecipe recipe) {
        List<ItemStack> ingredients = getIngredientList(recipe);
        if (ingredients.isEmpty()) return true;

        // Build a mutable copy of available items
        List<ItemStack> available = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                available.add(stack.copy());
            }
        }

        // Try to match each ingredient
        for (ItemStack needed : ingredients) {
            if (needed.isEmpty()) continue;
            int needCount = needed.getCount();
            for (ItemStack avail : available) {
                if (avail.isEmpty()) continue;
                if (ItemStack.isSameItemSameComponents(avail, needed)) {
                    int taken = Math.min(avail.getCount(), needCount);
                    avail.shrink(taken);
                    needCount -= taken;
                    if (needCount <= 0) break;
                }
            }
            if (needCount > 0) return false;
        }
        return true;
    }

    private static boolean hasSpaceForResult(Inventory inventory, ItemStack result) {
        int remaining = result.getCount();
        int maxStack = result.getMaxStackSize();

        // Check existing stacks that can merge
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, result)) {
                int space = maxStack - stack.getCount();
                if (space > 0) {
                    remaining -= Math.min(remaining, space);
                    if (remaining <= 0) return true;
                }
            }
        }

        // Check empty slots
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                remaining -= maxStack;
                if (remaining <= 0) return true;
            }
        }

        return remaining <= 0;
    }

    private static void consumeIngredients(Inventory inventory, CraftingRecipe recipe) {
        List<ItemStack> ingredients = getIngredientList(recipe);

        for (ItemStack needed : ingredients) {
            if (needed.isEmpty()) continue;
            int needCount = needed.getCount();

            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;
                if (ItemStack.isSameItemSameComponents(stack, needed)) {
                    int taken = Math.min(stack.getCount(), needCount);
                    stack.shrink(taken);
                    needCount -= taken;
                    if (stack.isEmpty()) {
                        inventory.setItem(i, ItemStack.EMPTY);
                    }
                    if (needCount <= 0) break;
                }
            }
        }
    }

    private static List<ItemStack> getIngredientList(CraftingRecipe recipe) {
        List<ItemStack> result = new ArrayList<>();

        if (recipe instanceof ShapedRecipe shaped) {
            for (Ingredient ing : shaped.getIngredients()) {
                ItemStack[] matches = ing.getItems();
                if (matches.length > 0) {
                    result.add(matches[0].copy());
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (Ingredient ing : shapeless.getIngredients()) {
                ItemStack[] matches = ing.getItems();
                if (matches.length > 0) {
                    result.add(matches[0].copy());
                }
            }
        }

        return result;
    }
}

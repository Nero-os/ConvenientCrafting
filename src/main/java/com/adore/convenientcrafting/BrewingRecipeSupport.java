package com.adore.convenientcrafting;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Shared helpers for virtual brewing recipes.
 */
public final class BrewingRecipeSupport {
    public static final ResourceLocation RECIPE_TYPE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "brewing");

    private static final String VIRTUAL_RECIPE_PREFIX = "brewing/";
    private static final HexFormat HEX = HexFormat.of();

    private BrewingRecipeSupport() {
    }

    public static ResourceLocation buildRecipeId(ItemStack input, ItemStack ingredient) {
        ResourceLocation containerId = BuiltInRegistries.ITEM.getKey(input.getItem());
        ResourceLocation potionId = getPotionId(input).orElse(null);
        ResourceLocation ingredientId = BuiltInRegistries.ITEM.getKey(ingredient.getItem());
        if (containerId == null || potionId == null || ingredientId == null) {
            return null;
        }

        return ResourceLocation.fromNamespaceAndPath(
                ConvenientCrafting.MODID,
                VIRTUAL_RECIPE_PREFIX
                        + encode(containerId) + "/"
                        + encode(potionId) + "/"
                        + encode(ingredientId)
        );
    }

    public static boolean isBrewingRecipeId(ResourceLocation recipeId) {
        return ConvenientCrafting.MODID.equals(recipeId.getNamespace())
                && recipeId.getPath().startsWith(VIRTUAL_RECIPE_PREFIX);
    }

    public static Optional<BrewingRecipeKey> parseRecipeId(ResourceLocation recipeId) {
        if (!isBrewingRecipeId(recipeId)) {
            return Optional.empty();
        }

        String[] parts = recipeId.getPath().substring(VIRTUAL_RECIPE_PREFIX.length()).split("/");
        if (parts.length != 3) {
            return Optional.empty();
        }

        ResourceLocation containerId = decode(parts[0]);
        ResourceLocation potionId = decode(parts[1]);
        ResourceLocation ingredientId = decode(parts[2]);
        if (containerId == null || potionId == null || ingredientId == null) {
            return Optional.empty();
        }

        return Optional.of(new BrewingRecipeKey(containerId, potionId, ingredientId));
    }

    public static Optional<ResourceLocation> getPotionId(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return contents.potion()
                .flatMap(Holder::unwrapKey)
                .map(ResourceKey::location);
    }

    public static ItemStack createPotionStack(RegistryAccess registries, ResourceLocation containerId, ResourceLocation potionId) {
        Optional<Item> container = BuiltInRegistries.ITEM.getOptional(containerId);
        if (container.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Optional<Holder.Reference<Potion>> potion = registries.registryOrThrow(Registries.POTION).getHolder(potionId);
        return potion
                .map(holder -> PotionContents.createItemStack(container.get(), holder))
                .orElse(ItemStack.EMPTY);
    }

    public static ItemStack createIngredientStack(ResourceLocation ingredientId) {
        return BuiltInRegistries.ITEM.getOptional(ingredientId)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private static String encode(ResourceLocation id) {
        return HEX.formatHex(id.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static ResourceLocation decode(String value) {
        try {
            return ResourceLocation.tryParse(new String(HEX.parseHex(value), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record BrewingRecipeKey(ResourceLocation containerId, ResourceLocation potionId, ResourceLocation ingredientId) {
    }
}

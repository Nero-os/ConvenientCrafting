package com.adore.convenientcrafting.compat;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmithingRecipe;

//? if >=1.21.4 {
/*import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
*///?}

/** Minecraft recipe API differences, shared by the client and the authoritative server. */
public final class RecipeCompat {
    private RecipeCompat() {}

    public static ItemStack[] items(Ingredient ingredient) {
        //? if >=1.21.4 {
        /*return ingredient.items().map(holder -> new ItemStack(holder)).toArray(ItemStack[]::new);
        *///?} else {
        return ingredient.getItems();
        //?}
    }

    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        //? if >=1.21.4 {
        /*return recipe.placementInfo().ingredients();
        *///?} else {
        return recipe.getIngredients();
        //?}
    }

    public static ItemStack result(Recipe<?> recipe, HolderLookup.Provider registries) {
        //? if >=1.21.4 {
        /*ContextMap context = new ContextMap.Builder().withParameter(SlotDisplayContext.REGISTRIES, registries).create(SlotDisplayContext.CONTEXT);
        return recipe.display().stream()
                .flatMap(display -> display.result().resolveForStacks(context).stream())
                .findFirst().orElse(ItemStack.EMPTY);
        *///?} else {
        return recipe.getResultItem(registries);
        //?}
    }

    public static ResourceLocation id(RecipeHolder<?> holder) {
        //? if >=1.21.4 {
        /*return holder.id().location();
        *///?} else {
        return holder.id();
        //?}
    }

    public static Optional<RecipeHolder<?>> byId(RecipeManager manager, ResourceLocation id) {
        //? if >=1.21.4 {
        /*return manager.byKey(ResourceKey.create(Registries.RECIPE, id));
        *///?} else {
        return manager.byKey(id);
        //?}
    }

    public static boolean isTemplateIngredient(SmithingRecipe recipe, ItemStack stack) {
        //? if >=1.21.4 {
        /*return Ingredient.testOptionalIngredient(recipe.templateIngredient(), stack);
        *///?} else {
        return recipe.isTemplateIngredient(stack);
        //?}
    }

    public static boolean isBaseIngredient(SmithingRecipe recipe, ItemStack stack) {
        //? if >=1.21.4 {
        /*return Ingredient.testOptionalIngredient(recipe.baseIngredient(), stack);
        *///?} else {
        return recipe.isBaseIngredient(stack);
        //?}
    }

    public static boolean isAdditionIngredient(SmithingRecipe recipe, ItemStack stack) {
        //? if >=1.21.4 {
        /*return Ingredient.testOptionalIngredient(recipe.additionIngredient(), stack);
        *///?} else {
        return recipe.isAdditionIngredient(stack);
        //?}
    }

    public static Stream<Holder.Reference<Potion>> potions(RegistryAccess registries) {
        //? if >=1.21.4 {
        /*return registries.lookupOrThrow(Registries.POTION).listElements();
        *///?} else {
        return registries.registryOrThrow(Registries.POTION).holders();
        //?}
    }
}

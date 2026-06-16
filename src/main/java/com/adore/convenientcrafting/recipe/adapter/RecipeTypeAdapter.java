package com.adore.convenientcrafting.recipe.adapter;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Describes how Convenient Crafting treats a recipe type at the shared indexing layer.
 */
public interface RecipeTypeAdapter {
    ResourceLocation typeId();

    boolean supports(Recipe<?> recipe);

    boolean supportsNestedCrafting();

    String duplicateKey(Recipe<?> recipe, HolderLookup.Provider registries);
}

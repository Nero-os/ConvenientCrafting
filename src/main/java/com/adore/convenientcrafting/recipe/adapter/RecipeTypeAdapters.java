package com.adore.convenientcrafting.recipe.adapter;

import com.adore.convenientcrafting.recipe.RecipeSupport;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registry for recipe type adapters used by Convenient Crafting.
 *
 * <p>Adapters keep recipe-type decisions in one place so new workstation types do not
 * accidentally share crafting, indexing, or duplicate-removal rules with vanilla
 * crafting-table recipes.</p>
 */
public final class RecipeTypeAdapters {
    public static final ResourceLocation CRAFTING = ResourceLocation.parse("minecraft:crafting");
    public static final ResourceLocation SMITHING = ResourceLocation.parse("minecraft:smithing");
    public static final ResourceLocation BREWING = ResourceLocation.parse("minecraft:brewing");
    public static final ResourceLocation STONECUTTING = ResourceLocation.parse("minecraft:stonecutting");

    private static final List<RecipeTypeAdapter> BUILT_IN_RECIPE_ADAPTERS = List.of(
            new ClassBackedRecipeTypeAdapter(CRAFTING, CraftingRecipe.class, true),
            new ClassBackedRecipeTypeAdapter(SMITHING, SmithingRecipe.class, false),
            new ClassBackedRecipeTypeAdapter(STONECUTTING, StonecutterRecipe.class, true)
    );

    private static final Set<String> BUILT_IN_TYPE_IDS = Set.of(
            normalize(CRAFTING),
            normalize(SMITHING),
            normalize(BREWING),
            normalize(STONECUTTING)
    );

    private RecipeTypeAdapters() {
    }

    public static Optional<RecipeTypeAdapter> findBuiltInAdapter(Recipe<?> recipe) {
        return BUILT_IN_RECIPE_ADAPTERS.stream()
                .filter(adapter -> adapter.supports(recipe))
                .findFirst();
    }

    public static boolean isBuiltInRecipeType(ResourceLocation typeId) {
        return typeId != null && BUILT_IN_TYPE_IDS.contains(normalize(typeId));
    }

    public static boolean supportsBuiltInRecipe(Recipe<?> recipe) {
        return findBuiltInAdapter(recipe).isPresent();
    }

    public static boolean supportsNestedCrafting(Recipe<?> recipe) {
        return findBuiltInAdapter(recipe)
                .map(RecipeTypeAdapter::supportsNestedCrafting)
                .orElse(false);
    }

    public static String buildDuplicateKey(Recipe<?> recipe, HolderLookup.Provider registries) {
        return findBuiltInAdapter(recipe)
                .map(adapter -> adapter.duplicateKey(recipe, registries))
                .orElseGet(() -> buildTypedDuplicateKey(recipe, registries));
    }

    private static String buildTypedDuplicateKey(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack result = recipe.getResultItem(registries);
        List<String> ingredientKeys = RecipeSupport.getNonEmptyIngredients(recipe).stream()
                .map(RecipeTypeAdapters::ingredientKey)
                .sorted()
                .collect(Collectors.toList());

        ResourceLocation typeId = RecipeSupport.getRecipeTypeId(recipe);
        String typeKey = typeId != null ? typeId.toString() : "unknown";
        if (ingredientKeys.isEmpty()) {
            return "recipe:" + typeKey + ":" + resultKey(result);
        }

        return "simple:" + typeKey + ":" + resultKey(result) + ":" + String.join("|", ingredientKeys);
    }

    private static String ingredientKey(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .map(RecipeTypeAdapters::resultKey)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String resultKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemKey = itemId != null ? itemId.toString() : "unknown:" + stack.getItem();
        return itemKey + "x" + stack.getCount() + stack.getComponentsPatch();
    }

    private static String normalize(ResourceLocation typeId) {
        return typeId.toString().toLowerCase(Locale.ROOT);
    }

    private record ClassBackedRecipeTypeAdapter(
            ResourceLocation typeId,
            Class<?> recipeClass,
            boolean supportsNestedCrafting
    ) implements RecipeTypeAdapter {
        @Override
        public boolean supports(Recipe<?> recipe) {
            return recipeClass.isInstance(recipe);
        }

        @Override
        public String duplicateKey(Recipe<?> recipe, HolderLookup.Provider registries) {
            return buildTypedDuplicateKey(recipe, registries);
        }
    }
}

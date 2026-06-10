package com.adore.convenientcrafting.recipe;

import com.adore.convenientcrafting.config.Config;
import com.adore.convenientcrafting.recipe.unlock.ClientRecipeUnlocks;
import com.adore.convenientcrafting.recipe.unlock.RecipeUnlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 便捷合成支持的配方类型判断工具。
 */
public final class RecipeSupport {
    private RecipeSupport() {
    }

    /**
     * 判断配方是否属于模组内置支持的类型。
     *
     * @param recipe 要检查的配方
     * @return 工作台或锻造台配方返回 {@code true}
     */
    public static boolean isBuiltInSupported(Recipe<?> recipe) {
        return recipe instanceof CraftingRecipe || recipe instanceof SmithingRecipe;
    }

    public static boolean isBuiltInRecipeType(ResourceLocation typeId) {
        if (typeId == null) {
            return false;
        }

        String normalized = typeId.toString().toLowerCase(Locale.ROOT);
        return normalized.equals("minecraft:crafting")
                || normalized.equals("minecraft:smithing")
                || normalized.equals("minecraft:brewing");
    }

    public static boolean isBuiltInRecipeTypeEnabled(Player player, ResourceLocation typeId) {
        if (!isBuiltInRecipeType(typeId)) {
            return false;
        }

        if (player instanceof ServerPlayer) {
            return RecipeUnlocks.isBuiltinRecipeTypeEnabled(typeId);
        }

        return ClientRecipeUnlocks.isBuiltinRecipeTypeEnabled(typeId);
    }

    /**
     * 判断配方是否属于配置额外启用的简单配方类型。
     *
     * @param recipe 要检查的配方
     * @return 配方类型在配置列表中时返回 {@code true}
     */
    public static boolean isAdditionalRecipeTypeEnabled(Recipe<?> recipe) {
        ResourceLocation typeId = getRecipeTypeId(recipe);
        if (typeId == null) {
            return false;
        }

        String normalizedTypeId = typeId.toString().toLowerCase(Locale.ROOT);
        return ClientRecipeUnlocks.isAdditionalRecipeTypeEnabled(typeId)
                || Config.ADDITIONAL_RECIPE_TYPES.get().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedTypeId::equals);
    }

    /**
     * 判断配方是否可按简单材料列表直接合成。
     *
     * @param recipe 要检查的配方
     * @param registries 注册表访问器
     * @return 配置启用、产物有效且材料列表非空时返回 {@code true}
     */
    public static boolean isConfiguredSimpleRecipe(Recipe<?> recipe, HolderLookup.Provider registries) {
        return isAdditionalRecipeTypeEnabled(recipe)
                && !recipe.getResultItem(registries).isEmpty()
                && !getNonEmptyIngredients(recipe).isEmpty();
    }

    /**
     * 判断玩家是否可以使用该配方类型的便捷合成。
     *
     * @param player 玩家
     * @param recipe 要检查的配方
     * @return 配方类型受支持且玩家已解锁时返回 {@code true}
     */
    public static boolean isUnlockedFor(Player player, Recipe<?> recipe) {
        ResourceLocation typeId = getRecipeTypeId(recipe);
        if (typeId == null) {
            return false;
        }
        if (isBuiltInRecipeType(typeId) && !isBuiltInRecipeTypeEnabled(player, typeId)) {
            return false;
        }
        if (player instanceof ServerPlayer) {
            return RecipeUnlocks.isUnlocked(player, typeId);
        }
        return ClientRecipeUnlocks.isUnlocked(typeId);
    }

    /**
     * 获取配方中的非空材料列表。
     *
     * @param recipe 要解析的配方
     * @return 去除空材料后的列表
     */
    public static List<Ingredient> getNonEmptyIngredients(Recipe<?> recipe) {
        List<Ingredient> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.getItems().length > 0) {
                ingredients.add(ingredient);
            }
        }
        return ingredients;
    }

    /**
     * 为配方生成用于列表去重的签名。
     *
     * <p>签名基于产物和材料集合。材料会排序，因此同一产物、同一材料集合的配方会被视为重复。</p>
     *
     * @param recipe 要生成签名的配方
     * @param registries 注册表访问器
     * @return 去重签名
     */
    public static String buildDuplicateKey(Recipe<?> recipe, HolderLookup.Provider registries) {
        ItemStack result = recipe.getResultItem(registries);
        List<String> ingredientKeys = getNonEmptyIngredients(recipe).stream()
                .map(RecipeSupport::ingredientKey)
                .sorted()
                .collect(Collectors.toList());

        if (ingredientKeys.isEmpty()) {
            return "recipe:" + getRecipeTypeId(recipe) + ":" + resultKey(result);
        }

        return "simple:" + resultKey(result) + ":" + String.join("|", ingredientKeys);
    }

    /**
     * 获取配方类型注册名。
     *
     * @param recipe 要检查的配方
     * @return 配方类型 ID；无法获取时返回 {@code null}
     */
    public static ResourceLocation getRecipeTypeId(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        return BuiltInRegistries.RECIPE_TYPE.getKey(type);
    }

    private static String ingredientKey(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .map(RecipeSupport::resultKey)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String resultKey(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemKey = itemId != null ? itemId.toString() : "unknown:" + stack.getItem();
        return itemKey + "x" + stack.getCount() + stack.getComponentsPatch();
    }
}

package com.adore.convenientcrafting.recipe.unlock;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 客户端缓存的玩家已解锁配方类型。
 */
public final class ClientRecipeUnlocks {
    private static final Set<String> UNLOCKED_RECIPE_TYPES = new HashSet<>();
    private static final Set<String> ENABLED_BUILTIN_RECIPE_TYPES = new HashSet<>();
    private static final Set<String> ENABLED_ADDITIONAL_RECIPE_TYPES = new HashSet<>();

    private ClientRecipeUnlocks() {
    }

    /**
     * 覆盖客户端已解锁配方类型缓存。
     *
     * @param recipeTypeIds 服务端同步的配方类型 ID
     */
    public static void setUnlockedRecipeTypes(Collection<String> recipeTypeIds) {
        UNLOCKED_RECIPE_TYPES.clear();
        recipeTypeIds.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(UNLOCKED_RECIPE_TYPES::add);
    }

    public static void setEnabledBuiltinRecipeTypes(Collection<String> recipeTypeIds) {
        ENABLED_BUILTIN_RECIPE_TYPES.clear();
        recipeTypeIds.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(ENABLED_BUILTIN_RECIPE_TYPES::add);
    }

    /**
     * 覆盖客户端的服务端额外启用配方类型缓存。
     *
     * @param recipeTypeIds 服务端额外启用的配方类型 ID
     */
    public static void setEnabledAdditionalRecipeTypes(Collection<String> recipeTypeIds) {
        ENABLED_ADDITIONAL_RECIPE_TYPES.clear();
        recipeTypeIds.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(ENABLED_ADDITIONAL_RECIPE_TYPES::add);
    }

    /**
     * 判断客户端缓存中是否已解锁指定配方类型。
     *
     * @param recipeTypeId 配方类型 ID
     * @return 已解锁时返回 {@code true}
     */
    public static boolean isUnlocked(ResourceLocation recipeTypeId) {
        return UNLOCKED_RECIPE_TYPES.contains(recipeTypeId.toString().toLowerCase(Locale.ROOT));
    }

    public static boolean isBuiltinRecipeTypeEnabled(ResourceLocation recipeTypeId) {
        return ENABLED_BUILTIN_RECIPE_TYPES.contains(recipeTypeId.toString().toLowerCase(Locale.ROOT));
    }

    /**
     * 判断服务端是否额外启用了指定配方类型。
     *
     * @param recipeTypeId 配方类型 ID
     * @return 已额外启用时返回 {@code true}
     */
    public static boolean isAdditionalRecipeTypeEnabled(ResourceLocation recipeTypeId) {
        return ENABLED_ADDITIONAL_RECIPE_TYPES.contains(recipeTypeId.toString().toLowerCase(Locale.ROOT));
    }
}

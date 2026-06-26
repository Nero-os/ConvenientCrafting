package com.adore.convenientcrafting.config;

import com.adore.convenientcrafting.recipe.adapter.RecipeTypeAdapters;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组公共配置定义。
 *
 * <p>该类集中声明 NeoForge 配置项，并在配置加载时提供必要的值校验。</p>
 */
public class Config {
    /**
     * 配置规格构建器。
     */
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_INVENTORY_SORTING = BUILDER
            .comment(
                    "Whether to enable the player inventory sort button and middle-click inventory sorting.",
                    "Disable this to avoid conflicts with dedicated inventory sorting mods."
            )
            .translation("convenientcrafting.configuration.enableInventorySorting")
            .define("enableInventorySorting", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CONTAINER_SORTING = BUILDER
            .comment(
                    "Whether to enable storage container sort buttons and middle-click container sorting.",
                    "Applies to supported storage containers such as chests, barrels, and shulker boxes."
            )
            .translation("convenientcrafting.configuration.enableContainerSorting")
            .define("enableContainerSorting", true);

    public static final ModConfigSpec.BooleanValue ENABLE_CATEGORY_BAGS = BUILDER
            .comment(
                    "Whether category bag entry points are enabled.",
                    "When disabled, category bags stay registered for save compatibility, but are hidden from the creative tab and their recipes are disabled."
            )
            .translation("convenientcrafting.configuration.enableCategoryBags")
            .define("enableCategoryBags", true);

    public static final ModConfigSpec.BooleanValue ENABLE_BAG_AUTO_PICKUP = BUILDER
            .comment(
                    "Whether picked-up matching items are automatically inserted into category bags in the player inventory.",
                    "When disabled, picked-up items use the normal vanilla inventory pickup flow."
            )
            .translation("convenientcrafting.configuration.enableBagAutoPickup")
            .define("enableBagAutoPickup", true);

    public static final ModConfigSpec.BooleanValue USE_BAG_CONTENTS_FOR_CRAFTING = BUILDER
            .comment(
                    "Whether items stored inside category bags count as available materials for Convenient Crafting.",
                    "When disabled, Convenient Crafting only uses visible inventory and container stacks."
            )
            .translation("convenientcrafting.configuration.useBagContentsForCrafting")
            .define("useBagContentsForCrafting", true);

    public static final ModConfigSpec.BooleanValue ENABLE_ALT_MATERIAL_COMPACTION = BUILDER
            .comment(
                    "Whether Alt inventory sorting compacts mineral materials using repeated-material crafting recipes.",
                    "When disabled, Alt sorting behaves like normal inventory sorting."
            )
            .translation("convenientcrafting.configuration.enableAltMaterialCompaction")
            .define("enableAltMaterialCompaction", true);

    /**
     * 内置支持的一键合成工作台配方类型。
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENABLED_BUILTIN_RECIPE_TYPES = BUILDER
            .comment(
                    "Built-in vanilla workstation recipe type ids enabled for Convenient Crafting.",
                    "Remove an id from this list to disable that workstation type.",
                    "Supported built-in ids: minecraft:crafting, minecraft:smithing, minecraft:brewing, minecraft:stonecutting"
            )
            .translation("convenientcrafting.configuration.enabledBuiltinRecipeTypes")
            .defineListAllowEmpty(
                    "enabledBuiltinRecipeTypes",
                    List.of(
                            RecipeTypeAdapters.CRAFTING.toString(),
                            RecipeTypeAdapters.SMITHING.toString(),
                            RecipeTypeAdapters.BREWING.toString(),
                            RecipeTypeAdapters.STONECUTTING.toString()
                    ),
                    () -> "",
                    Config::validateBuiltinRecipeType
            );

    /**
     * 额外启用的一键合成配方类型。
     *
     * <p>默认内置支持工作台、锻造台和酿造台。这里的额外类型会按“普通材料列表 + 固定产物”处理，
     * 适合整合包作者显式启用已确认安全的模组工作方块配方。</p>
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_RECIPE_TYPES = BUILDER
            .comment(
                    "Additional recipe type ids enabled for Convenient Crafting.",
                    "Only enable recipe types that are safe to craft directly from ingredients and result.",
                    "Extra recipe types also need a matching recipeTypeUnlockItems rule before players can unlock them.",
                    "Examples: create:mechanical_crafting, malum:spirit_infusion"
            )
            .translation("convenientcrafting.configuration.additionalRecipeTypes")
            .defineListAllowEmpty("additionalRecipeTypes", List.of(), () -> "", Config::validateResourceLocation);

    /**
     * 配方类型对应的解锁物品配置。
     *
     * <p>格式为 {@code recipe_type=item_id,item_id}。玩家获得任意一个解锁物品后，
     * 会永久解锁对应配方类型。</p>
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RECIPE_TYPE_UNLOCK_ITEMS = BUILDER
            .comment(
                    "Recipe type unlock rules. Format: recipe_type=item_id,item_id",
                    "Players permanently unlock the recipe type after obtaining any listed item.",
                    "Built-in defaults are always available for minecraft:crafting, minecraft:smithing, minecraft:brewing, and minecraft:stonecutting.",
                    "Examples: malum:spirit_infusion=malum:spirit_altar, create:mechanical_crafting=create:mechanical_crafter"
            )
            .translation("convenientcrafting.configuration.recipeTypeUnlockItems")
            .defineListAllowEmpty("recipeTypeUnlockItems", List.of(), () -> "", Config::validateUnlockRule);

    /**
     * NeoForge 使用的最终配置规格。
     */
    public static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * 校验配置值是否是合法资源位置。
     *
     * @param obj 配置列表中的原始值
     * @return 值是合法资源位置字符串时返回 {@code true}
     */
    private static boolean validateResourceLocation(final Object obj) {
        return obj instanceof String location && ResourceLocation.tryParse(location) != null;
    }

    private static boolean validateBuiltinRecipeType(final Object obj) {
        if (!(obj instanceof String location)) {
            return false;
        }

        return RecipeTypeAdapters.isBuiltInRecipeType(ResourceLocation.tryParse(location.trim()));
    }

    /**
     * 校验配方类型解锁规则格式。
     *
     * @param obj 配置列表中的原始值
     * @return 规则格式合法时返回 {@code true}
     */
    private static boolean validateUnlockRule(final Object obj) {
        if (!(obj instanceof String rule)) {
            return false;
        }

        String[] parts = rule.split("=", 2);
        if (parts.length != 2 || ResourceLocation.tryParse(parts[0].trim()) == null) {
            return false;
        }

        String[] itemIds = parts[1].split(",");
        if (itemIds.length == 0) {
            return false;
        }

        for (String itemId : itemIds) {
            if (ResourceLocation.tryParse(itemId.trim()) == null) {
                return false;
            }
        }

        return true;
    }
}

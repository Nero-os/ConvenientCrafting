package com.adore.convenientcrafting;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
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

    /**
     * 是否在公共初始化阶段输出泥土方块注册名。
     */
    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    /**
     * 示例整型配置值。
     */
    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    /**
     * 示例数字日志前缀文本。
     */
    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    /**
     * 以资源位置字符串形式配置的物品列表。
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    /**
     * 额外启用的一键合成配方类型。
     *
     * <p>默认只内置支持工作台和锻造台。这里的额外类型会按“普通材料列表 + 固定产物”处理，
     * 适合整合包作者显式启用已确认安全的模组工作方块配方。</p>
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADDITIONAL_RECIPE_TYPES = BUILDER
            .comment(
                    "Additional recipe type ids enabled for Convenient Crafting.",
                    "Only enable recipe types that are safe to craft directly from ingredients and result.",
                    "Extra recipe types also need a matching recipeTypeUnlockItems rule before players can unlock them.",
                    "Examples: create:mechanical_crafting, malum:spirit_infusion"
            )
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
                    "Built-in defaults are always available for minecraft:crafting and minecraft:smithing.",
                    "Examples: malum:spirit_infusion=malum:spirit_altar, create:mechanical_crafting=create:mechanical_crafter"
            )
            .defineListAllowEmpty("recipeTypeUnlockItems", List.of(), () -> "", Config::validateUnlockRule);

    /**
     * NeoForge 使用的最终配置规格。
     */
    static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * 校验配置中的物品资源位置是否存在于注册表。
     *
     * @param obj 配置列表中的原始值
     * @return 值是有效物品资源位置时返回 {@code true}
     */
    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }

    /**
     * 校验配置值是否是合法资源位置。
     *
     * @param obj 配置列表中的原始值
     * @return 值是合法资源位置字符串时返回 {@code true}
     */
    private static boolean validateResourceLocation(final Object obj) {
        return obj instanceof String location && ResourceLocation.tryParse(location) != null;
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
